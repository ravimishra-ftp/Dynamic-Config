# dynamic-config
Update configuration on real time without restart of Server.
Update configuration on request scope without affecting other end users on same environment.
Update Log Level on run time.

This module is built on spring boot, uses eureka discovery service to find all instances by service id and update configuration on each container. In conventional spring refresh scope, we need to raise config PR and get it merge then hit actuator refresh end-point and then it will take effect and that may take very moderate amount of time when needed a quick fix, also it is expensive in terms of performance when we have many beans under refresh scope. So, this will fix our config quickly and then get config PR merge only.

Request scope config update enable automation scripts to test feature on/off scenarios without modifying config and impacting other end users. So, we can achieve 100% test coverage for all combinations of configurations.

Log level update provide ability to change for a package or a specific class, also it can update to a specific container using IP address.
