# Assignment: Last value price service
Version: 2.0

## Business requirements:
Your task is to design and implement a service for keeping track of the last price for financial instruments.
Producers will use the service to publish prices and consumers will use it to obtain them.

The price data consists of records with the following fields:
* id: a string field to indicate which instrument this price refers to.
* asOf: a date time field to indicate when the price was determined.
* payload: the price data itself, which is a flexible data structure.

Producers should be able to provide prices in batch runs.
The sequence of uploading a batch run is as follows:
1. The producer indicates that a batch run is started
2. The producer uploads the records in the batch run in multiple chunks of 1000 records.
3. The producer completes or cancels the batch run.

Consumers can request the last price record for a given id.
    The last value is determined by the asOf time, as set by the producer.
On completion, all prices in a batch run should be made available at the same time.
Batch runs which are cancelled can be discarded.

The service should be resilient against producers which call the service methods in an incorrect order,
or clients which call the service while a batch is being processed.

## Technical requirements:

* Provide a working Java application which implements the business requirements
* The service interface should be defined as a Java API, so consumers and producers can be assumed to run in the same JVM.
* For the purpose of the exercise, we are looking for an in-memory solution (rather than one that relies on a database).
* Demonstrate the functionality of the application through unit tests
* You can use any open source libraries. Please include gradle or maven project definitions for the dependencies.

The purpose of this exercise is for you to demonstrate that you can analyse business requirements and convert them into clean code.
Please apply the same standards to the code which you would if this was a production system.
Comments in the code to illustrate design decisions are highly appreciated.
Bonus points can be scored by analysing performance characteristics and considering possible improvements.
