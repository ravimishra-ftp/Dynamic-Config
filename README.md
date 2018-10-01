# Dynamic-Config Feature List
---

### * Real Time Config/Feature Flag Updates
### * Config Updates at Request Level
### * Dynamic Log Level Changes
---

dynamic-config-<latest-version>-SNAPSHOT.

How to use in respective MS:

1.Add this module as external library in the root service build.gradle file.

	'dynamic-config': [
        [group: 'com.example', name: 'dynamic-config', version: properties.'dcp-dynamic-config.version'],
     ],

2.Add the version of core module in the root service gradle.properties file.

	dynamic-config.version=<latest-version>

3.Add the compile dependency in the respective module's build.gradle.

	dependencies {
    compile([
            libraries.'dynamic-config',
    ])

4.Add below property in your orchestrator service with appropriate values.

```
dynamicConfig:
  apiEnabled: true
  requestScopeEnabled: true
  basePackage: com.example     	    # <base packages for the scanning of this component>
  readTimeout: 2000                 # default value can be optimize according to number of containers
  connectionTimeout: 1000           # default value can be optimize according to number of containers
  maxTotalConnections: 2            # default value can be optimize according to number of containers
  serviceIdAndContextPath:          # define all the service ids and context path
    my-service-v1: my-service-rs1
    my-service-v2: my-service-rs2
    <and so on>
```
5.Add below property in your child service to enable and disable.

```
dynamicConfig:
  apiEnabled: true
  requestScopeEnabled: true
```
6.Adding security feature in these API create can be done defining any random UUID key and define as.

```
ms:
  internal:
    annotation.enable: true
    api:
      key: 83835120-d028-11e7-9cbb-91e6b056d526 # <OR we can encrypt using encryption to make more secure>
```
---

#### * Real Time Config/Feature Flag Updates:

This module is built on spring boot, uses eureka discovery service to find all instances by service id and update configuration on each container of that service.

Note: API is capable to update all primitive, wrapper, String, and BigDecimal value type. It is not recommended to update any collection or user defined type property value.

Created two APIs to do that - Please contact Aurora Team for Post Man Collection wirh different security key for the environments.

1 - getConfig: Will show all the updatable configurations for given service id in url.

    URL: GET - {{protocol}}{{host}}/order-api/v1/config/order-service-v1

2 - updateConfig: Will let you update config for given service id using property key and value

    URL: PUT - {{protocol}}{{host}}/order-api/v1/config/bag-service-v1

#### * Config Updates at Request Level:
Request scope config update enable automation scripts to test feature on/off scenarios without modifying config and impacting other end users. So, we can achieve 100% test coverage for all combinations of configurations.
To do that, we need to put @EnableDynamicConfig on the configuration class and pass flag name and value as header in the request.

#### * Dynamic Log Level Changes:
Provide ability to update the log level for a package or a specific class. Also has ability to update log level for specific container using IP address.
Created API to do that - Please contact Aurora Team for Post Man Collection wirh different security key for the environments.

1 - updateLog: will let you update log for given service id or any specific ip address for that service with package name and log level

    URL: PUT - {{protocol}}{{host}}/order-api/v1/config/log/order-service-v1
    Or
	URL: PUT - {{protocol}}{{host}}/order-api/v1/config/log/order-service-v1/192.168.1.145

---
### *Disclaimer

Library is tested under moderate load model based Applications (max 9 Containers).
Stability and Robustness of the APIs may vary based on different factors i.e. platform, technology stacks, number of containers and current load. Discrepancy with respect to any unwanted behavior while using this library will completely be on the end-users own risk. It is advised to test and get certify these API from performance team under load model of your applications.

---
