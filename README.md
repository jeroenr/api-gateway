Api Gateway [![Build Status](https://travis-ci.org/jeroenr/api-gateway.svg?branch=master)](https://travis-ci.org/jeroenr/api-gateway) [![License](https://img.shields.io/hexpm/l/plug.svg)](http://www.apache.org/licenses/LICENSE-2.0)
=========================
The Api Gateway is the entry point of a microservice infrastructure (see [api gateway pattern](http://microservices.io/patterns/apigateway.html)). The main problem it solves is "How do the clients of a Microservices-based application access the individual services?". It handles the first layer of authentication and routes the incoming requests (like a reverse proxy) to the corresponding service, based on the mapping in its service registry.

## Requirements
- Sbt 0.13.*

## Running
```bash 
$ sbt run
```
By default this will start the gateway interface on http://localhost:8080 and the management interface on http://localhost:8081

## Building a docker image
This project is using the [Docker plugin of the Sbt Native Packager](http://www.scala-sbt.org/sbt-native-packager/formats/docker.html) to generate a docker image using:
```bash
$ sbt docker:publishLocal
```
Then you can simply publish your docker image to your favorite docker repository. For instance for the Google Container Registry:
```bash
$ docker tag com.github.jeroenr/api-gateway eu.gcr.io/my-docker-registry/api-gateway
$ gcloud docker push eu.gcr.io/my-docker-registry/api-gateway
```

## Deploying in Kubernetes
Since the Api gateway currently relies on the Kubernetes API for service discovery the first thing we would need is an access token. The easiest way to do this would be through [service accounts](https://kubernetes.io/docs/admin/authentication/#service-account-tokens). After that you need descriptor files for the [Kubernetes deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/) and service. Below is a quick walkthrough the steps. I assume you're familiar with Kubernetes' terminology and the [kubectl](https://kubernetes.io/docs/user-guide/kubectl-overview/) commandline tool.
### Create service account
The steps below will create a service account and associated [secret](https://kubernetes.io/docs/concepts/configuration/secret/) to be used by the Api gateway [pod](https://kubernetes.io/docs/concepts/workloads/pods/pod/).
```bash
$ kubectl create serviceaccount my-service-account
serviceaccount "my-service-account" created
```
Great, now a new service account has been created and under the hood also an associated secret which we can retrieve by:
```bash
$ kubectl get serviceaccounts my-service-account -o yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  # ...
secrets:
- name: my-service-account-token-1yvwg
```
As you can see in our example the generated secret is called "my-service-account-token-1yvwg". Now we can setup our K8s deployment and service descriptor. Here's an [example config](https://github.com/jeroenr/api-gateway/blob/master/k8s-descriptor.yaml). 

## Deploying on other platforms
Currently the Api gateway relies on the services endpoint from the Kubernetes API to poll for service updates (see [Automatic Service discovery section](#automatic-service-discovery)). If you want to deploy the Api gateway on a different platform you would have to:
* Mock this endpoint and manually update the service registry (see (#manually-updating-the-service-registry). You could also rely on something like Consul and write a sync script.  
* Make a PR to add a flag in the configuration to disable automatic service discovery
* Make a PR to support different pluggable service discovery modules

## Features

### Authentication
As mentioned the Api gateway is the first layer of the authentication flow. If a request is made to a secured route (i.e. a route which maps to a service with the flag "secured" set) it will request a [JWT](https://jwt.io/) access token from an authentication service (e.g. https://github.com/cupenya/auth-service which retrieves a user claim based on a reference token in a domain cookie and generates a JWT token for this claim). If a valid JWT token is returned the call is forwarded to the corresponding service and the JWT token is passed in the request header as an Oauth bearer token for further authorization to be done by the backing service. 

If no valid JWT token is returned the error response will be forwarded to the caller. This could happen when for instance the user session has expired. The user could then "login" again using the "/auth/login" endpoint as follows:
```bash
$ curl -X POST \
  http://localhost:8080/auth/login \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json'
  -d '{
    "username": "my-user",
    "password": "my-pass"
}'
```
This call is just forwarded to the authentication service.

Here's a [Postman collection with all available Api calls](https://www.getpostman.com/collections/9b269b9f26bf0a3ce255)

### Automatic Service discovery
The Api gateway supports service discovery through the Kubernetes API using the [k8s-svc-discovery module](https://github.com/jeroenr/k8s-svc-discovery). It's polling the /api/v1/services endpoint for service and updates the routing / mapping based on the service metadata.

### Manually updating the service registry
There's also a dashboard Api which can be used to manually update the service registry. For instance to add a service:
```bash
$ curl -X POST \
  http://localhost:8081/services \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/json'
  -d '{
	"name": "my-user-service",
	"host": "localhost",
	"resource": "users",
	"port": 9090,
	"secured": false
}'
```
This will add a reverse proxy mapping from ```http://{api-gateway-host}:{api-gateway-port}/{api-gateway-prefix}/users``` to ```http://localhost:9090/users```. If no 'port' is specified it will default to 80. The flag 'secured' determines whether the Api gateway performs authentication checks and passes on the authentication info to the corresponding service. See [Authentication section](#authentication) for more information.

Here's a [Postman collection with all available Api calls](https://www.getpostman.com/collections/9b269b9f26bf0a3ce255)
