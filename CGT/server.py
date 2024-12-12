import asyncio
import logging
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
from typing import List,Union
from collections import defaultdict
import uvicorn
import re
import json
import networkx as nx
import matplotlib.pyplot as plt
from io import BytesIO
import base64
import os
from datetime import datetime
from fastapi.responses import JSONResponse
import matplotlib.cm as cm
import numpy as np
from networkx.drawing.nx_agraph import graphviz_layout 

app = FastAPI()

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')

class TraceData(BaseModel):
    trackingId: str
    method: str
    path: str
    requestIp: str
    host: str
    parentId: str
    eoi: int
    ess: int

nodes = defaultdict(int)
edges = {}

# Async lock to ensure thread safety in graph updates
graph_lock = asyncio.Lock()

config = {}

def load_config(config_file="cgt_config.json"):
    global config
    try:
        with open(config_file, "r") as f:
            config = json.load(f)
            print(config)
            logging.info("Configuration loaded successfully.")
    except Exception as e:
        logging.error(f"Error loading configuration: {e}")

# Normalize the endpoints only if configured by the user
def normalize_path(path: str) -> str:
    global config
    # If not enabled
    if not config.get("pattern_matching_enabled", False):
        return path

    for pattern in config.get("patterns", []):
        pattern_regex = re.escape(pattern["pattern"]).replace(r'\*', r'(.*?)')
        if re.match(pattern_regex, path):
            normalized_path = pattern["normalized_path"]
            return normalized_path
    
    return path

# Background task to process the trace data and update the graph
async def process_trace_batch(batch_data: List[TraceData]):
    async with graph_lock:
        for trace in batch_data:
            # normalize the endpoint if needed
            normalized_path = normalize_path(trace.path)

            nodes[normalized_path] += 1  

            if trace.parentId and trace.parentId != 'NA':
                # check for cycles [right now we only look for 2-hop cycles]
                # TODO: periodically, the graph should have an efficient cycle detection [because it will grow large!!] 
                normalized_parentId = normalize_path(trace.parentId)

                if normalized_parentId in edges.get(normalized_path, {}):  # Detects 2-hop cycle
                    logging.warning(f"Cycle detected when adding edge from {normalized_parentId} to {normalized_path}")
                
                if normalized_parentId not in edges:
                    edges[normalized_parentId] = {}

                if normalized_path not in edges[normalized_parentId]:
                    edges[normalized_parentId][normalized_path] = 0
                
                edges[normalized_parentId][normalized_path] += 1

                # Here an edge case might be the fact that the parent is not addedd to the nodes yet
                if normalized_parentId not in nodes:
                    nodes[normalized_parentId] = 0

# Endpoint to receive the trace data
@app.post("/track")
async def track_call(trace_data: List[TraceData], background_tasks: BackgroundTasks):
    background_tasks.add_task(process_trace_batch, trace_data)
    return {"message": "Trace data received successfully."}

def generate_graph_pdf():
    global config

    G = nx.DiGraph()

    for node, weight in nodes.items():
        G.add_node(node, weight=weight)

    for parent, children in edges.items():
        for child, weight in children.items():
            G.add_edge(parent, child, weight=weight)

    plt.figure(figsize=(10, 10))
    pos = nx.spring_layout(G, k=0.5, iterations=50)
    # pos = nx.nx_agraph.graphviz_layout(G, prog="dot")

    # Normalize node and edge weights to get a color spectrum
    node_weights = [G.nodes[node]["weight"] for node in G.nodes]
    norm = plt.Normalize(vmin=min(node_weights), vmax=max(node_weights))
    cmap = cm.coolwarm  
    node_colors = [cmap(norm(weight)) for weight in node_weights]

    edge_weights = [G[u][v]["weight"] for u, v in G.edges]
    norm = plt.Normalize(vmin=min(edge_weights), vmax=max(edge_weights))
    cmap = cm.coolwarm
    edge_colors = [cmap(norm(weight)) for weight in edge_weights]

    nx.draw_networkx_nodes(G, pos, node_color=node_colors, alpha=0.9)
    nx.draw_networkx_edges(G, pos, edge_color=edge_colors, alpha=0.9)

    # # Draw node labels (displaying the node weights)
    # node_labels = {node: f'({G.nodes[node]["weight"]})' for node in G.nodes}
    # nx.draw_networkx_labels(G, pos, labels=node_labels, font_size=8, font_color='black')

    # # Draw edge labels (displaying the edge weights)
    # edge_labels = {(u, v): f'{G[u][v]["weight"]}' for u, v in G.edges}
    # nx.draw_networkx_edge_labels(G, pos, edge_labels=edge_labels, font_size=8, font_color='black')

    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")

    visualization_storage = config.get("visualization_storage", "./callgraphs/")
    os.makedirs(os.path.dirname(visualization_storage), exist_ok=True)
    pdf_file_path = os.path.join(visualization_storage, f"{timestamp}.pdf")

    os.makedirs(os.path.dirname(pdf_file_path), exist_ok=True)

    plt.savefig(pdf_file_path, format="pdf")
    plt.close()

    return pdf_file_path

# Route to plot the graph and save it locally
@app.get("/visualize")
async def visualize_graph():
    pdf_path = generate_graph_pdf()
    return JSONResponse(content={"message": "Graph has been saved as PDF.", "pdf_path": pdf_path})

# Endpoint to view the current graph (node weights)
@app.get("/graph")
async def get_graph():
    return {
        "nodes": dict(nodes),  
        "edges": edges
    }

if __name__ == '__main__':
    load_config()
    uvicorn.run(app, host="0.0.0.0", port=8081)

