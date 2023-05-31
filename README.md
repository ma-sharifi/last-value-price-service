1- Performance test
2- diagram
3- clean code\

Producer -> Write to file, consumer read from file;

https://smallrye.io/smallrye-reactive-messaging/3.14.1/concepts/testing/

? How mock object help me to test this code?
?including cases where the producer produces data faster than the consumer can consume, or vice versa. Use assertions to verify that the expected behavior is met.
https://smallrye.io/smallrye-reactive-messaging/smallrye-reactive-messaging/3.3/testing/testing.html
   @BeforeAll
    public static void switchMyChannels() {
        InMemoryConnector.switchIncomingChannelsToInMemory("prices");
        InMemoryConnector.switchOutgoingChannelsToInMemory("processed-prices");
    }
@Test
    void test() {
        // 4. Retrieves the in-memory source to send message
        InMemorySource<Integer> prices = connector.source("prices");
        // 5. Retrieves the in-memory sink to check what is received
        InMemorySink<Integer> results = connector.sink("processed-prices");

        // 6. Send fake messages:
        prices.send(1);
        prices.send(2);
        prices.send(3);

        // 7. Check you have receives the expected messages
        Assertions.assertEquals(3, results.received().size());
    }

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
* Note: for the sake of simlicity I'm supposed payload is long not an object.

* The sequence of application is as follows:
    1- StartBatch
    2- Upload chunk of priceData as follows: <batchId, Chunk[1..1000]>,<batchId,Chunk[1001..2000]>,... <batchId,Chunk[n-1000...n]>
    3- Complete/Cancel batch: batch is finished.

## Datastructures
1- ConcurrentHashMap. I used Guava MapMaker as well, but the performance of ConcurrentHashMap was better.
2- Caffeine: because it has different eviction policy(by size and by time) and support different referenceType for key and value(weakKeys()/weakValues()/softValues() that project needs.
2- BlockingQueue: because it is a threadSafe

## Solution
* I wanted to save the data of each producer in different queue in order to multiple producer can produce data at the same time.
Kafka and ThreadLocal are the design I was looking for,so the solution is inspired by Kafka and ThreadLocal.

## First approach
Each batch had a UUID that specified that batch.
* There is a cache for holding all batch data based on batch id. Key is batchId and in value is the chunk of data of this batchId. Value is BlockingQueue.
In complete batch process, I priceData from completed BlockingQueue and compare osOf field with instrument.updatedAt if osOf is after updated updatedAt I update the database. For this approach, I hadn't considered storage/database speed.
Note: When we the speeds are not the same we will have the Queue, even at the store's counter and bank's counter.
![design1](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/24ea4bd6-c21e-4287-8bf2-986f62f140d0)

## Second approach
* Since storage/database is not as fast as RAM, a BlokingQueue added before storage/database.
As project progressed I found a problem with storage/database queue that had added in this step!
![desing2](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/147ba46d-932e-49a4-b7ec-efbe250c85c0)

## Third approach
If some instruments are still in the queue waiting for their turn, so the database has not been updated, but another producer has generated the same instruments, how can we compare the price data with the instrument that still is in the queue, not the database?
For example, InstrumentA is updated by producer1 but InstrumentA is still in the queue and it has not been persisted yet into storage/database, and producer2 produce a Price data of the instrumentA and now its producer2 turn to update instrumentA price?
Since instrumentA is not updated yet into storage/database, producer2 compare the price with notUpdated instrumentA (stale data will be read from the database).
If we read updated data from cache(is next to blocking queue, we put the instrument into them at once) instead of storage/database our problem will be solved.

Five threads are considered for reading data from the storage queue and updating storage (This number is extracted from the load test).
But user can start as many updater worker threads as required.
![final-final](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/4210d1b4-ef8b-4774-8cbd-bf512957ef62)

### Explain Price Tracking Service

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

### Weak reference prefer to strong reference
WeakReference: Objects stored in the cache are held using weak references.
This allows the garbage collector to collect and reclaim memory for cached objects when they are no longer strongly referenced elsewhere in the application.

* Define the object immutable, in order to use them safely in multithreading application.
?* At first used 3 different type of object as a cache, Map, MapMaker and Caffeine, because Caffeine. At last used Map as storage, because storage should have


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

please se how to improve system here improvement.md
