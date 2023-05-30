1- Perforamnce test
2- diagram
3- clean code
4- clean Maven

git remote add origin https://github.com/ma-sharifi/last-value-price-service.git
git branch -M main
git push -u origin main

# Last value price service V: 2.0
# MAHDI Note:  The last value is determined by the asOf time -> Stamped? Version? Think


## Business requirements:
Design a service for keeping track of the last price for financial instruments.
* Producers will use the service to publish prices 
* Consumers will use it to obtain them.

Question:
How can I find the latest price?


1- Can I use Caffeine cache?

2- "The producer uploads the records in the batch (made by multiple chunk of records) run in multiple chunks of 1000 records."
Assume there are 2001 records in total, the producer want to send them.
I was wondering which approach the question ask, A or B?

A:StartBatch-> Chunk[1..1000],Chunk[1001..2000],Chunk[2001] ->Complete/Cancel

Or:
B: StartBatch-> Chunk[1..1000]   ->Complete/Cancel
B: StartBatch-> Chunk[1001..2000]->Complete/Cancel
B: StartBatch-> Chunk[2001]      ->Complete/Cancel

When batch data (made by multiple chunk) should visible for consumer?

4-  


3- "The service should be resilient against clients which call the service while a batch is being processed."
I got it this way: When a 






