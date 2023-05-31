1- Performance test
2- diagram
3- clean code\
# Last value price service V: 2.0

## Business requirements:
Designed and implemented a service for keeping track of the last price for financial instruments.
* Producers will use the service to publish prices 
* Consumers will use it to obtain them.

## Definition
* BatchId: Every batch has an id. Each batch made by multiple chunk.
* Chunk: Every chunk made by multiple records of data.
* Storage: refer to database that used Map here instead of a real one.
* Models: We have 2 different objects, Instrument adn PriceData that PriceData is used for sending by producer to service.


## Solution
* The solution is inspired by how Kafka and ThreadLocal work.
## First approach
* There is a map for holding all batch data based on batch id (Call it thread in ThreadLocal). Save the chunk data into the value of this batchId. Value is BlockingQueue.
![design1](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/24ea4bd6-c21e-4287-8bf2-986f62f140d0)

## Second approach
* Since storage/database is not as fast as RAM, a BlokingQueue added before storage/database.
![desing2](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/147ba46d-932e-49a4-b7ec-efbe250c85c0)

## Third approach
As project progressed I found a problem with database queue!
What if data are still into queue and it have not update the database yet, but another producer produce the data for updating this instrument!
For example, InstrumentA is updated by producer1 but InstrumentA is still in the queue and it has not been persisted yet into storage/database, and producer2 produce a Price data of the instrumentA?
Since instrumentA is not updated yet into storage/database, producer2 compare the price with notUpdated instrumentA (because read stall data from databaes).
If we read updated data from cache(is next to blocking queue, we put the instrument intot hem at once) instead of storage/database our problem will be solved.

Five thread considered for reading data from storageQueue nad update storage (This number extracted from laod test).
But user can start as many updater worker threads as required.
![final-final](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/4210d1b4-ef8b-4774-8cbd-bf512957ef62)

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

* I used Caffeine, Guava MapMaker and Map as a storage, but at last I choose Map for sake of simplicity and it was faster in my test.

## References
* Used ConcurrentHashMap with strong reference as database here for holding the instrument data.
* WeakReference: A weak reference, simply put, is a reference that isn't strong enough to force an object to remain in memory. Weak references allow garbage collector's to collect it. It means that if no other strong references to the object exist, it can be garbage collected.
* Soft Reference: A weak reference object that remains in memory a bit more. Normally, it resists GC cycle until no memory is available and there is risk of OutOfMemoryError (in that case, it can be removed). It allows the object to be garbage collected if memory is low, but it tries to keep the object in memory as long as possible.
* Caffeine.weakKeys() stores keys using weak references. This allows entries to be garbage-collected if there are no other strong references to the keys.
I tried to used Caffeine.weakKeys() but after I sat it the data removed in the next API call. For improvement I need to add it to the project.WeakReference


## Concurrency
* Used, BlockingQueue, ConcurrentHashMap adn Caffeine, that make the applicatiob thread saftety, but when I reaed the data from one an put to anpther one it
* The performance of weka

## Cache
There are 3 situation that I need to use cache in this project.
1- Holding chunk of data until make the whole batch. If a consumer call start batch without finish the batch with compelete/cancel, data of this cache will be delete after a certain period of time.
2- Storage/database cache, when I put data into BlockingQueue to update storage, I put it into this cache to become reachable before updating the database. After storage read data are added to this cache as well. Data here can be last more, base on our needs.
3- Keeping how many update we had in a batch specified by batchId, it is used for metrics.

### Exception
Defined different Exceptions for different situations.
Provided a Global Exception handler to help handle exceptions in an easy way.

### Test
The system I used for test:
* JVM: Java HotSpot(TM) 64-Bit Server VM (17.0.2+8-LTS-86, mixed mode, emulated-client, sharing)
* Java: version 17.0.2, vendor Oracle Corporation
* GC: G1

* Device: MacBook Pro 2019
* CPU: 2.3 GHz 8-Core Intel Core i9
* RAM: 16 GB 2667 MHz DDR4
* OS: macOS Ventura 13.3.1
* Number of records in a batch: 5000
* Number of records in a chunks(partitionSize): 1000
* Number of requests: 1000
* Number of threads (Concurrent producer): 100

## Conclusion
1- If we have less item per chunk we will have more throughput.
2- When I increased the database updater worker thread to 5, the performance increased.
3- When I increased the number of concurrent producer to 100, the performance increased.
4- WeakReference has less performance than strongly referenced.

