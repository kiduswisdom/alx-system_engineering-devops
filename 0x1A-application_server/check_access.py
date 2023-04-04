#!/usr/bin/python3
"""
Checks that a student's code serves a page from the route
/airbnb-onepage/ on port 5000
"""
import sys

from fabric import Connection
from invoke import run
from io import StringIO
from paramiko import RSAKey
from time import sleep

host = sys.argv[1]
user = sys.argv[2]
rsa_key_file = sys.argv[3]
route = sys.argv[4]
curl_localhost = False
if len(sys.argv) >= 6 and sys.argv[5] == 'localhost':
    curl_localhost = True

rsa_key = RSAKey.from_private_key(open(rsa_key_file))
output = StringIO()

def curl_server(ip, route, conn=None):
    """ Sends request to student's server and returns response. """
    sleep(5)
    out = None
    if conn is None:
        output = run('curl -s '+str(ip)+str(route), hide=True, warn=True)
        out = output.stdout
    else:
        coutput = StringIO()
        conn.run('curl -s localhost'+str(route), shell="/bin/bash", out_stream=coutput, warn=True)
        out = coutput.getvalue()
    return out

with Connection(host, user=user, connect_kwargs={"pkey": rsa_key}) as c:
    c.run("netstat -na | grep '5000.* LISTEN'", shell="/bin/bash", out_stream=output, warn=True)
    if output.getvalue():
        print(curl_server(host, route, c if curl_localhost else None), end="")
        exit(0)
    else:
        c.run("bash -lc \"tmux new-session -d 'python3 AirBnB_clone_v2/web_flask/0-hello_route.py'\"", shell="/bin/bash", warn=True)
        for i in range(5):
            curl_output = curl_server(host, route, c if curl_localhost else None)
            if curl_output == "Hello HBNB!":
                print(curl_output, end="")
                c.run("sudo pkill python3", shell="/bin/bash", warn=True)
                exit(0)
        print(curl_output, end="")
        c.run("sudo pkill python3", shell="/bin/bash", warn=True)
        exit(0)
