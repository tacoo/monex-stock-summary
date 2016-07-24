PATH=$PATH:./firefox
gradle clean fatCapsule && xvfb-run java -jar build/libs/monex-stock-summary-1.0-capsule.jar
