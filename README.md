# monex-stock-summary
Login to Monex and download yearly stock trading activities(csv) and summarize by monthly.

## Requirements
+ Xvfb
+ FireFox
+ Java 1.8
+ Gradle 2.5

## Configure
Modify '/monex-stock-summary/src/main/resources/monex.properties'

## Build
``gradle clean jar``

## Run
``xvfb-run java -jar build/libs/monex-stock-summary-1.0.jar``

## Columns

- Credit that you deposit in that year.
- Gain that you earned in that year. Taxable.
- Gain(NoTax) that you earned in that year but non taxable.
- Yield that you received in that year.
- SalesTax/Fee that you already paid to buy stocks in that year.
- IncomTax that you paid to Japanese government in that year.
- Earned that you actually earned in that year including tax.

