import json
import re
import networkx as nx
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
import dash
import dash_core_components as dcc
import dash_html_components as html
import plotly.graph_objects as go
from collections import defaultdict
import argparse
import numpy as np

app = dash.Dash(__name__)


# Call class remains unchanged
class Call:
    def __init__(self, tracking_id, method, op_index, path, request_ip, host, parent_sender_id, curr_sender_id):
        self.tracking_id = tracking_id
        self.method = method
        self.op_index = op_index
        self.path = path
        self.request_ip = request_ip
        self.host = host
        self.parent_sender_id = parent_sender_id
        self.curr_sender_id = curr_sender_id

        # Extract the service name from the path
        self.service_name = (path.split("/"))[1]

    def __repr__(self):
        return f"Call({self.method}, {self.path}, opIndex={self.op_index}, parent={self.parent_sender_id}, curr={self.curr_sender_id}, service={self.service_name})"

class Trace:
    def __init__(self, tracking_id):
        self.tracking_id = tracking_id
        self.calls = []
    
    def add_call(self, single_call):
        if single_call not in self.calls:
            self.calls.append(single_call)
    
    def get_calls(self):
        return sorted(self.calls, key=lambda call: call.op_index)

    def __repr__(self):
        repr_str = f"Trace(tracking_id={self.tracking_id}, calls=["

        sorted_calls = self.get_calls()
        if sorted_calls:
            repr_str += ', '.join([repr(call) for call in sorted_calls[:3]])  # Limit to first 3 calls for brevity
            if len(sorted_calls) > 3:
                repr_str += ", ... " 
        repr_str += "])"
        
        return repr_str

class Node:
    def __init__(self, service, endpoint):
        self.service = service  
        self.endpoint = endpoint
        self.edges = []  # Outgoing edges

    def add_edge(self, edge):
        self.edges.append(edge)

    def __repr__(self):
        return f"Node({self.service}-{self.endpoint})"

class Edge:
    def __init__(self, start_node, end_node, weight=1):
        self.start_node = start_node 
        self.end_node = end_node
        self.weight = weight  # Weight [every time an edge is created between two nodes, the weight is incremented]

    def __repr__(self):
        return f"Edge({self.start_node.endpoint} -> {self.end_node.endpoint}, weight={self.weight})"

class CallGraph:
    def __init__(self):
        self.nodes = {}  # Stored by endpoint [assuming that the endpoint-paths are unique]
        self.edges = []  

    def add_node(self, service, endpoint):
        """Add a node to the graph."""
        if endpoint not in self.nodes:
            self.nodes[endpoint] = Node(service, endpoint)

    def add_edge(self, start_ep, end_ep, weight=1):
        """Add an edge between two nodes."""
        if start_ep not in self.nodes or end_ep not in self.nodes:
            return 
        
        start_node = self.nodes[start_ep]
        end_node = self.nodes[end_ep]

        # if the edge already exists, just update the weight
        existing_edge = next((edge for edge in start_node.edges if edge.end_node == end_node), None)
        if existing_edge:
            existing_edge.weight += weight
        else:
            edge = Edge(start_node, end_node, weight)
            start_node.add_edge(edge)
            self.edges.append(edge)

    def get_edges(self):
        """Return all edges in the graph."""
        return self.edges

    def get_nodes(self):
        """Return all nodes in the graph."""
        return list(self.nodes.values())

    def __repr__(self):
        """Provide a string representation of the graph."""
        node_repr = ", ".join([repr(node) for node in self.nodes.values()])
        edge_repr = ", ".join([repr(edge) for edge in self.edges])
        return f"Graph(\n  Nodes: [{node_repr}],\n  Edges: [{edge_repr}]\n)"

    def get_networkx_graph(self):
        """Generate a NetworkX graph representation."""
        G = nx.DiGraph()
        for node in self.nodes.values():
            G.add_node(node.endpoint, label=node.service)
        for edge in self.edges:
            G.add_edge(edge.start_node.endpoint, edge.end_node.endpoint, weight=edge.weight)
        return G

def fix_log_format(line):
    """
    Converts a log line from Python-style dictionary to valid JSON format.
    """
    # Replace single quotes with double quotes for keys and values
    line = line.replace("'", '"')
    
    # JSON keys must not have trailing commas (in case there's one at the end of the dictionary)
    line = re.sub(r',\s*}', '}', line)
    line = re.sub(r',\s*\]', ']', line)
    
    return line

def process_log_file(file_path):
    calls_by_tracking_id = defaultdict(list)
    traces = {}  # A dictionary to hold the trace objects based on their tracking id

    # Read the file line by line
    with open(file_path, 'r') as file:
        for line in file:
            if "trackingId" in line:
                fixed_line = fix_log_format(line.strip())  # Validate JSON format
                try:
                    trace_data = json.loads(fixed_line)

                    # Create a Trace object
                    reqCall = Call(
                        tracking_id=trace_data['trackingId'],
                        method=trace_data['method'],
                        op_index=trace_data['opIndex'],
                        path=trace_data['path'],
                        request_ip=trace_data['requestIp'],
                        host=trace_data['host'],
                        parent_sender_id=trace_data['parentSenderId'],
                        curr_sender_id=trace_data['currSenderId']
                    )

                    # Append the call to its own trace
                    if trace_data['trackingId'] in traces:
                        traces[trace_data['trackingId']].add_call(reqCall)
                    else:
                        trace = Trace(tracking_id=trace_data['trackingId'])
                        trace.add_call(reqCall)
                        traces[trace_data['trackingId']] = trace

                except json.JSONDecodeError as e:
                    print(f"Error decoding JSON: {e} for line: {fixed_line}")

    # Now for each trace, construct the graph
    cg = CallGraph()

    for tid, trace in traces.items():
        for call in trace.get_calls():
            cg.add_node(call.service_name, call.path)
            if call.parent_sender_id != -1:
                parent_id = call.parent_sender_id
                parent_call = next((c for c in trace.get_calls() if c.curr_sender_id == parent_id), None)
                if parent_call:
                    cg.add_edge(parent_call.path, call.path)

    return cg

def generate_graph(cg):
    node_x = []
    node_y = []
    node_text = []
    edge_x = []
    edge_y = []
    edge_text = []

    # Color of nodes is based on the service they are in
    node_services = [node.service for node in cg.get_nodes()]
    unique_services = list(set(node_services))
    colors = plt.cm.get_cmap('tab20', len(unique_services))  # Generate 'len(unique_services)' colors
    service_colors = {service: colors(i) for i, service in enumerate(unique_services)}

    # Convert the RGBA color to a hex color code
    service_colors = {service: mcolors.rgb2hex(color[:3]) for service, color in service_colors.items()}
    node_colors = [service_colors[service] for service in node_services]

    pos = nx.spring_layout(cg.get_networkx_graph(), seed=42)

    for node, (x, y) in pos.items():
        node_x.append(x)
        node_y.append(y)
        node_text.append(node)

    for edge in cg.edges:
        start_pos = pos[edge.start_node.endpoint]
        end_pos = pos[edge.end_node.endpoint]
        edge_x.append(start_pos[0])
        edge_y.append(start_pos[1])
        edge_x.append(end_pos[0])
        edge_y.append(end_pos[1])
        edge_text.append(f"Weight: {edge.weight}")

    edge_trace = go.Scatter(
        x=edge_x,
        y=edge_y,
        line=dict(width=0.5, color='gray'),
        hoverinfo='text',
        mode='lines+text',
        text=edge_text,
        showlegend=False
    )

    # Use a color scale for nodes, and assign each node a color based on its service
    node_trace = go.Scatter(
        x=node_x,
        y=node_y,
        mode='markers',
        hoverinfo='text',
        marker=dict(
            size=20,
            color=node_colors,  # Use the color map values for nodes
        ),
        text=node_text,
        showlegend=False
    )

    # Create the legend traces based on the service colors
    legend_traces = []
    for service, color in service_colors.items():
        legend_traces.append(go.Scatter(
            x=[None], y=[None],
            mode='markers',
            marker=dict(size=10, color=color),
            name=service,  # Service name as the legend
            hoverinfo='none', showlegend=True
        ))

    layout = go.Layout(
        title='Call Graph',
        showlegend=True,
        hovermode='closest',
        margin=dict(b=0, l=0, r=0, t=40),
        xaxis=dict(showgrid=False, zeroline=False),
        yaxis=dict(showgrid=False, zeroline=False),
        width=1700,
        height=900 
    )

    # Add both the node trace and the legend traces
    return go.Figure(data=[edge_trace, node_trace] + legend_traces, layout=layout)

def main():
    parser = argparse.ArgumentParser(description="Visualize or process a call graph.")
    parser.add_argument('--visualize', action='store_true', help="Start the Dash app to visualize the graph")
    args = parser.parse_args()

    cg = process_log_file("tmp.log")

    # If the --visualize flag is provided, start the Dash app
    if args.visualize:
        graph_figure = generate_graph(cg)
        app.layout = html.Div([
            dcc.Graph(figure=graph_figure)
        ])
        app.run_server(debug=True, port=8089)
    else:
        print("Call graph generated, but not visualized.")
        print(cg)  # or do something else with `cg`


if __name__ == '__main__':
    main()
