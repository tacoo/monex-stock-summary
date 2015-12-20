# monex-stock-summary
Login to Monex and download yearly stock trading activities(csv) and summarize by monthly.
But currently this is not working because the calculater does not use average acquisition cost so the total income of the year will not be accurate.

## Requirements
+ Xvfb
+ FireFox
+ Java 1.8
+ Gradle 2.5

## Configure
Modify '/monex-stock-summary/src/main/resources/monex.properties'

## Build
``gradle clean fatCapsule``

## Run
``xvfb-run java -jar build/libs/monex-stock-summary-1.0-capsule.jar``
