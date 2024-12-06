import threading
import json
import logging
from flask import Flask, request, jsonify
from concurrent.futures import ThreadPoolExecutor
import dash
from dash import dcc, html, Input, Output
import plotly.graph_objs as go
import networkx as nx
import matplotlib.colors as mcolors
import matplotlib.pyplot as plt

app = Flask(__name__)

dash_app = dash.Dash(__name__, server=app, url_base_pathname='/visualize/')

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
        self.service_name = (path.split("/"))[1]  # Extract service name from the path

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
        self.weight = weight  # Increment weight every time an edge is created

    def __repr__(self):
        return f"Edge({self.start_node.endpoint} -> {self.end_node.endpoint}, weight={self.weight})"

class CallGraph:
    def __init__(self):
        self.nodes = {}  # Stored by endpoint
        self.edges = []

    def add_node(self, service, endpoint):
        if endpoint not in self.nodes:
            self.nodes[endpoint] = Node(service, endpoint)

    def add_edge(self, start_ep, end_ep, weight=1):
        if start_ep not in self.nodes or end_ep not in self.nodes:
            return
        
        start_node = self.nodes[start_ep]
        end_node = self.nodes[end_ep]

        # Update edge weight if it already exists
        existing_edge = next((edge for edge in start_node.edges if edge.end_node == end_node), None)
        if existing_edge:
            existing_edge.weight += weight
        else:
            edge = Edge(start_node, end_node, weight)
            start_node.add_edge(edge)
            self.edges.append(edge)

    def get_edges(self):
        return self.edges

    def get_nodes(self):
        return list(self.nodes.values())

    def __repr__(self):
        node_repr = ", ".join([repr(node) for node in self.nodes.values()])
        edge_repr = ", ".join([repr(edge) for edge in self.edges])
        return f"Graph(\n  Nodes: [{node_repr}],\n  Edges: [{edge_repr}]\n)"

    def get_networkx_graph(self):
        import networkx as nx
        G = nx.DiGraph()
        for node in self.nodes.values():
            G.add_node(node.endpoint, label=node.service)
        for edge in self.edges:
            G.add_edge(edge.start_node.endpoint, edge.end_node.endpoint, weight=edge.weight)
        return G

# Global Variables
traces = {}
cg = CallGraph()
log_file_path = "trace_log.txt"
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s', filename=log_file_path)

# ThreadPoolExecutor for async logging
executor = ThreadPoolExecutor(max_workers=2)
graph_lock = threading.Lock()  # Lock to ensure thread safety when updating the graph

# Asynchronous function to process the trace and update the graph
def async_track_call(call):
    trackingId = call.tracking_id

    # Ensure thread-safe access to traces and cg
    with graph_lock:
        if trackingId in traces:
            traces[trackingId].add_call(call)
        else:
            trace = Trace(trackingId)
            trace.add_call(call)
            traces[trackingId] = trace

        if call.op_index == 0:  # Root of the trace
            for call in traces[trackingId].get_calls():
                cg.add_node(call.service_name, call.path)
                if call.parent_sender_id != -1:
                    parent_id = call.parent_sender_id
                    parent_call = next((c for c in traces[trackingId].get_calls() if c.curr_sender_id == parent_id), None)
                    if parent_call:
                        cg.add_edge(parent_call.path, call.path)
            
            print("Triggering dash update")
            dash_app.layout.children[-1].data = True

# Function to add a call
def add_call(trackingId, method, path, requestIp, host, opIndex, parentSenderId, currSenderId):

    reqCall = Call(
        tracking_id=trackingId,
        method=method,
        op_index=opIndex,
        path=path,
        request_ip=requestIp,
        host=host,
        parent_sender_id=parentSenderId,
        curr_sender_id=currSenderId
    )
    # Asynchronously process the call graph update
    executor.submit(async_track_call, reqCall)

@app.route('/track', methods=['POST'])
def track_call():
    data = request.json  # Expecting JSON with caller, callee, and timestamp
    print(data)
    
    if not data or 'trackingId' not in data or 'path' not in data:
        return jsonify({"error": "Invalid data. 'trackingId' and 'path' are required."}), 400

    trackingId = data['trackingId']
    method = data['method']
    path = data['path']
    requestIp = data['requestIp']
    host = data['host']
    opIndex = data['opIndex']
    parentSenderId = data['parentSenderId']
    currSenderId = data['currSenderId']

    # Track the method call and trigger async graph update
    add_call(trackingId, method, path, requestIp, host, opIndex, parentSenderId, currSenderId)

    return jsonify({"message": "Trace added successfully."}), 200

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

    return go.Figure(data=[edge_trace, node_trace] + legend_traces, layout=layout)

@dash_app.callback(
    [Output('call-graph', 'figure'),
     Output('trigger-update', 'data')],  # Reset the store value after updating the graph
    [Input('trigger-update', 'data')]
)
def update_graph(trigger_update):
    if trigger_update:
        fig = generate_graph(cg)
        # Reset the trigger flag to False after updating the graph
        return fig, False
    return dash.no_update, dash.no_update

dash_app.layout = html.Div([
    html.H1("Call Graph Visualization"),
    dcc.Graph(id="call-graph"),
    dcc.Store(id="trigger-update", data=False)
])

# Start the server on port 8081
if __name__ == '__main__':
    # TODO: based on if the user wants the visualizaiton to be part of the tracking
    app.run(host='0.0.0.0', port=8081, debug=True)
