from flask import Flask

# Initialize the Flask application
app = Flask(__name__)

# Define a route for the home page
@app.route('/')
def hello_world():
    return 'Hello, World!'

# Run the Flask app
if __name__ == '__main__':
    # Run the server on host '0.0.0.0' so it is accessible externally
    app.run(host='0.0.0.0', port=5000, debug=True)

