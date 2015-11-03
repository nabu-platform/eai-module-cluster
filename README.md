# Clusters

Clusters are required for a number of things:

- caching: caching can be either local or clustered
- redundancy: if a server goes down, another server must take over
- performance: spread the load over multiple servers

A cluster -in the nabu sense- consists of:

- a logical name (the id of the cluster item in the repository)
- a list of servers that belong to it

A server can identify which cluster it belongs to by checking the servers in the cluster until it finds itself

Cluster items can either exist in all environments (and are deployed) or they are created only in the environment that they are used.