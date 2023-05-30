## What is solution? a short description of the solution and explaining some design decisions

Designed and implemented a service for keeping track of the last price for financial instruments.
Producers will use the service to publish prices and consumers will use it to obtain them.

## First solution
* The solution is inspired by how Kafka and ThreadLocal work.
* There is a map for holding all batch data based on batch id (Call it thread in ThreadLocal). Save the chunk data into the value of this batchId. Value is BlockingQueue.  
![design1](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/24ea4bd6-c21e-4287-8bf2-986f62f140d0)

* Since storage/database is not as fast as RAM, a BlokingQueue added before storage/database.  
![desing2](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/147ba46d-932e-49a4-b7ec-efbe250c85c0)

As project progressed I found a problem with database queue!
What if data are still into queue and it have not update the database yet, but another producer produce the data for updating this instrument! 
For example, InstrumentA is updated by producer1 but InstrumentA is still in the queue and it has not been persisted yet into storage/database, and producer2 produce a Price data of the instrumentA? 
Since instrumentA is not updated yet into storage/database, producer2 compare the price with notUpdated instrumentA (because read stall data from databaes). 
If we read updated data from cache(is next to blocking queue, we put the instrument intot hem at once) instead of storage/database our problem will be solved.

Five thread considered for reading data from storageQueue nad update storage (This number extracted from laod test). 
But user can start as many updater worker threads as required.  
![final-final](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/4210d1b4-ef8b-4774-8cbd-bf512957ef62)

## Definition
* BatchId: Every batch has an id. Each batch made by multiple chunk.
* Chunk: Every chunk made by multiple records of data.
* Storage: refer to database that used Map here instead of a real one.
* Models: We have 2 different objects, Instrument adn PriceData that PriceData is used for sending by producer to service. 

### Map for saving objects
Because every object has one Id, I saved them into Map.

### Weak reference prefer to strong reference
WeakReference: Objects stored in the cache are held using weak references. 
This allows the garbage collector to collect and reclaim memory for cached objects when they are no longer strongly referenced elsewhere in the application.

* Define the object immutable, in order to use them safely in multithreading application.
?* At first used 3 different type of object as a cache, Map, MapMaker and Caffeine, because Caffeine. At last used Map as storage, because storage should have 


* The sequence of application is as follows: StartBatch-> Chunk[1..1000],Chunk[1001..2000],... Chunk[n]->Complete/Cancel
* Start Batch: Generate a batchId and return it as a response to producer. It is used for 2 purposes:
  * For resiliency: Protect the service against producers which call the service methods in an incorrect order
  * For holding chunk data as a batch in a Map
* Upload Chunk: Consume batchId and chunk of priceData and put the data into BlockingQueue. With batchId it gets the blockingQueue of this batch then add this chunk into own BlockingQueue. It also protects the client from unComplete/Canceled data.
* 
* Complete Batch: The most important one. We are here, because we know the batch is finished and it's the start of our process. it do the following tasks:
  * Read completed batch data: All chunk of the batch received and put into own BlockingQueue. Get the BlockingQueue of this batch by batchId.
  * Update the instrument into storage: Storage is slower that cache and queue. If we update the database here is taking time and it will become a bottleneck. Instead, we put the instrument needs to be updated into queue and cache in order updater worker thread read them and update the storage. At firs the was no cache next to updater queue, but after test, I found what if data is still into queue and it have not update the database yet, but another producer produce the that data, what would happen! For example: InstrumentA is updated by produce1 but InstrumentA is still n the queue an it has not been updated yet into database, and producer2 produce a Price data of instrumentA? Because instrumentA is not updated yet, producer2 compare the price with notUpdated instrumentA. If we read updated data from cache(is next to blocking queue, we put the instrument intot hem at once) instead of storage it will be solved.
  * Read the instrument related to the price: Instrument is in the storage that is not fast enough, but the question is: what if the storage is not updated yet? Because of this I Used a cache next to the storage updater Queue. Read the instrument (Cache/Storage) compare with current priceData.asOf if current priceData.asOf is After instrument.updatedAt create a new (update) instrument and put them into blocking queue and Cache.  
  * Remove batchId from the priceData cache, it means this batch is finished. (The same as cancel batch)
* Updater worker: I don't update storage directly when batch is completed, because storage is slow! We know storage(database) is much slower than cache. Instead of updating the database directly when batch is completed, we put the data need to be updated into shared BlockingQueue in the complete batch section. Multi thread can consume this BlockingQueue. The Service starts five threads by default. User can start as many threads as required.
* Cancel Batch: Remove batchId from the priceData cache, it means this batch is finished.
* Read Instrument: It can't see the data of a batch as are receiving and before complete the batch. After that batch is completed by call Complete method with batchId, the data will be visible for consumer. It reads an instrument from the cache at first not the storage/database, because maybe data are still in the queue not storage/database Because the change maybe not be effected into storage yet, because it is still in the queue since storage is slow.
*
* I used Caffeine, Guava MapMaker and Map as a storage, but at last I choose Map for sake of simplicity and it was faster in my test.

## References
* Caffeine.weakKeys() stores keys using weak references. This allows entries to be garbage-collected if there are no other strong references to the keys.
* Used ConcurrentHashMap with strong reference as database here for holding the instrument data.

## Concurrency
* Used, BlockingQueue, ConcurrentHashMap adn Caffeine, that make the applicatiob thread saftety, but when I reaed the data from one an put to anpther one it 
* The performance of weka

### Exception
Defined different Exceptions for different situations.
Provided a Global Exception handler to help handle exceptions in an easy way.