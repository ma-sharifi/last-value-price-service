# Last value price service V: 2.0

## Business requirements:
Designed and implemented a service for keeping track of the last price for financial instruments.
* Producers will use the service to publish prices 
* Consumers will use it to obtain them.

## How to run the application?
This is a Spring Boot application that is written with Java 17.

```shell
mvn spring-boot:rund
```
## How to test service?
Provided 2 junit test for Testing Endpoint(PriceTrackingEndpointTest.java) and Service(InMemoryPriceTrackingServiceTest.java), that you can run.

## Assumptions when solving the challenge:
* Simplicity is more important than other things. Tried to have a small code.
* Storage here is a database that must persist data into the disk, it means storage is much slower than other data structure we used.
* Assumed I can use Caffeine as a cache.
* For sake of simplicity I did not define a new object as Payload, I assumed payload is price and nothing else!
* Because we always check the instrument date with the price data date, and sometimes we need to update the storage, I take this application as "read bounded".
* We have enough memory.

## Definition
1. BatchId: Every batch has an id. Each batch made by multiple chunk.
2. Chunk: Every chunk made by multiple records of data.
3. Storage: Refer to a database that used Map here instead of a real one.
4. Models: We have 2 different objects, Instrument and PriceData that PriceData is used for sending by producer to service as multiple chunk.

## The sequence of application is as follows:
1. Start a batch with producing a batchId.
2. Upload chunk of priceData as follows: <batchId, Chunk[1..1000]>,<batchId,Chunk[1001..2000]>,... <batchId,Chunk[n-1000...n]>
3. Complete/Cancel batch: batch is finished.

## Datastructures
1. ConcurrentHashMap. I used Guava MapMaker as well, but the performance of ConcurrentHashMap was a little bit better.
2. Caffeine: because it has different eviction policy(by size and by time) and support different referenceType for key and value(weakKeys()/weakValues()/softValues() that project needs.
3. BlockingQueue: because it is a threadSafe Queue.

## Solution
* I wanted to save the data of each producer in different queue in order to multiple producer can produce data at the same time.
Kafka and ThreadLocal are the design I was looking for,so the solution is inspired by Kafka and ThreadLocal.

### First approach
Each batch had a UUID that specified that batch.
* There is a cache for holding all batch data based on batch id. Key is batchId and in value is the chunk of data of this batchId. Value is BlockingQueue.
In complete batch process, I priceData from completed BlockingQueue and compare osOf field with instrument.updatedAt if osOf is after updated updatedAt I update the database. For this approach, I hadn't considered storage/database speed.
Note: When we the speeds are not the same we will have the Queue, even at the store's counter and bank's counter.
![first](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/36d7a44a-2584-4bbe-94ad-fb212737c568)


### Second approach
* Since storage/database is not as fast as RAM, a BlokingQueue added before storage/database.
As project progressed I found a problem with storage/database queue that had added in this step!
![second](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/e7ef7bf4-16b0-4634-aeb3-ae08014c690c)

### Third approach
If some instruments are still in the queue waiting for their turn, so the database has not been updated, but another producer has generated the same instruments, how can we compare the price data with the instrument that still is in the queue, not the database?
For example, InstrumentA is updated by producer1 but InstrumentA is still in the queue and it has not been persisted yet into storage/database, and producer2 produce a Price data of the instrumentA and now its producer2 turn to update instrumentA price?
Since instrumentA is not updated yet into storage/database, producer2 compare the price with notUpdated instrumentA (stale data will be read from the database).
If we read updated data from cache(is next to blocking queue, we put the instrument into them at once) instead of storage/database our problem will be solved.

Five threads are considered for reading data from the storage queue and updating storage (This number is extracted from the load test).
But user can start as many updater worker threads as required.

![last](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/c27b8491-af27-4c7b-bef8-9d2616013bed)


## Explain Price Tracking Service (InMemoryPriceTrackingService.java)

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

## Memory References
* Used ConcurrentHashMap with strong reference as database here for holding the instrument data.
* WeakReference: A weak reference, simply put, is a reference that isn't strong enough to force an object to remain in memory. Weak references allow garbage collector's to collect it. It means that if no other strong references to the object exist, it can be garbage collected. I wanted to set the keys and values of the Caffeine cache weak for priceDataChunkCache, but it needs more time to add them, at this moment when I added weak references to Caffeine it can't return referenced data. It needs more time to consider.
* Soft Reference: A weak reference object that remains in memory a bit more. Normally, it resists GC cycle until no memory is available and there is risk of OutOfMemoryError (in that case, it can be removed). It allows the object to be garbage collected if memory is low, but it tries to keep the object in memory as long as possible. I just wanted this for the cache of the instrument data(instrumenCache), because it stores the instrument data, we hold them as much as possible. 
* Note: I tried to used Caffeine.weakKeys() but after I sat it the data removed in the next API call. For improvement, I need to add it to the project.WeakReference

## Concurrency
* Used, BlockingQueue, ConcurrentHashMap adn Caffeine, that make the application thread safe, but when I read the data from one an put to another one it I need to keep it atomic, I used synchronized for sake of simplicity.
* Define the objects immutable (java record), in order to use them safely in multithreading application.

## Caffeine as a Cache
There are 3 situation that I need to use cache in this project.
1. Temporarily storing chunks of data until making the whole batch. If a consumer call to start a batch without finishing the batch with complete/cancel, data from this cache will be deleted after a certain period of time.
2. Storage/database cache, when I put data into BlockingQueue to update storage, I put it into this cache to become reachable before updating the database by queue. After read from storage data will be stored to this cache as well in order to decrease database access. Data here can last more, based on our needs.
3. Keeping how many update we had in a batch specified by batchId, it is used for metrics.

## REST API (PriceTrackingEndpoint.java)
For the sake of simplicity, I defined all operations in one endpoint. It would be better to isolate the thread pool of batch from other instrument operations. Thus batch will not affect an instrument API call.
Described all API as follows:

### /batch/start
1. Start a batch:
* **POST**`/instruments/batch/start` HTTP Status: 200=OK
* 
2. Upload price data:
* **POST**`/instruments/batch/{batchId}/upload` HTTP Status: 202=ACCEPTED
* Status code is accepted because we don't do any process on our chunk
*
3. Complete a batch:
* **POST**`/instruments/batch/{batchId}/complete` HTTP Status: 201=CREATED
* Status code is created because we create a batch completely in server
*
4. Cancel a batch:
* **POST**`/instruments/batch/{batchId}/cancel` HTTP Status: 200=OK
* 
5. Get the last price:
* **GET**`/instruments/{instrumentId}` HTTP Status: 200=OK

`
Note: I provided 4 more APIs for test scenarios.
`
An instrument json:
```json
  {
  "id": "2",
  "updatedAt": "2021-06-11T12:44:06",
  "price": 2
}
```
An price data json:
```json
  {
  "id": "2",
  "asOf": "2021-06-11T12:44:16",
  "payload": 2
}
```
## Exception
* Defined different Exceptions for different situations.
* Provided a Global Exception handler to help handle exceptions in an easy way.

## Test
The test was the hardest part. This part looks like Kafka. So, how do we test Kafka?  
As you know it's hard. I tried different ways, generating random input, and random files put a file between consumer and producer, but all of them were not the thing I was looking for. I was looking for a simple way.  
[smallrye](https://smallrye.io/smallrye-reactive-messaging/smallrye-reactive-messaging/3.3/testing/testing.html) provided an in memory library for testing kafka. That put a queue in the same machine between them.  
I asked myself, how can I use this idea to test this code in a simple way?  
Eventually, I found it. The problem is about putting updated data into storage, but here I had a cache. It means the latest data are in the cache.  
The data I organized are changed by a simple law in every step. After organizing the data of Price Data and Instrument, I was just looking for this organized data, not random data.  
The getLastPrice method that is called by the consumer gets its data from the cache. The problem solve, After I completed a batch I need to assert the result of getLastPrice with instrument Cache in the serviceTest class.  

```java
service.completeBatchRun(batchId);
for (Instrument instrument : instrumentExpectedList) {
  Instrument instrumentActual = service.getLastPrice(instrument.id()); //read data by service
  Instrument instrumentInCache = service.getInstrumenCache().getIfPresent(instrument.id()); //read data from cache
  assertThat(instrumentActual).isEqualTo(instrumentInCache);
}
```

* Note: My JMeter had a problem, I did not manage to use it, I put the application under load with the InMemoryPriceTrackingServiceTest.java and saw the result on VisualVM and JConsole.

* The system I used for test:
* JVM: Java HotSpot(TM) 64-Bit Server VM (17.0.2+8-LTS-86, mixed mode, emulated-client, sharing)
* Java: version 17.0.2, vendor Oracle Corporation
* GC: G1
* Device: MacBook Pro 2019
* CPU: 2.3 GHz 8-Core Intel Core i9
* RAM: 16 GB 2667 MHz DDR4
* OS: macOS Ventura 13.3.1
* Number of records in a batch: 100_000
* Number of records in a chunks(partitionSize): 1_000
* Number of requests: 5000
* Number of threads (Concurrent producer): 100 , the number of request*thread is constant.

#TIME: 23713 ms ;requestNo: 500 ;threadsNo: 100 ;recordNo: 100000 ;partitionSize: 10000 ;service.size: 100000 ->#max: 4096 MB; free: 992 MB; total: 4096 MB; core: 16
![gc-100-thread](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/77b9ee13-bd0c-4935-9804-4434a728b2a8)

#TIME: 40618 ms ;requestNo: 1000 ;threadsNo: 50 ;recordNo: 100000 ;partitionSize: 10000 ;service.size: 100000 ->#max: 4096 MB; free: 1722 MB; total: 4096 MB; core: 16
![gc-50-thread](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/8eba9cb5-b813-45a0-8a0f-6365ba8d2286)


#TIME: 24348 ms ;requestNo: 500 ;threadsNo: 100 ;recordNo: 100000 ;partitionSize: 1000 ;service.size: 100000 ->#max: 4096 MB; free: 944 MB; total: 4096 MB; core: 16
![gc-th100-psize10000](https://github.com/ma-sharifi/last-value-price-service/assets/8404721/584aaf1a-81cd-4ad5-98c8-406571ca8e9b)

When we increased our concurrent request (more concurrent consumer) G1 Old Generation will be used, there is no difference between using partition size.
But the performance about 60% improved.
* It means if we have more consumer concurrent with the fix request*thread(50_000) number, the performance would be improved.


## Improvement
* Use the Back pressure mechanism. Back pressure can help by limiting the queue size (user ArrayBlockingQueue for storage updater with a fixed size) and PriceData Cache(Each entry belongs to one batch, we can fix the size to 100 batches), thereby maintaining a high throughput rate and good response times for jobs already in the queue. Once the queue/cache fills up, clients get a server busy or HTTP 503 status code to try again later.
* The constant numbers in the application should be set from the statistics data that is extracted from the production environment.
* Separate batch operations from other API calls. Thus batch will not affect an instrument API call. if we don't do this, when the batch is under high load, the normal operation of instrument like getLatestPrice will be slow.
* Using Redis/Kafka as publisher/subscriber
* Use redis as a cache to save our data when we put them in the StorageQueue in order to know which records is waiting to persis into storage.
* Use Grid computing tools for processing priceData and compare them with Instrument data and send them Queue and Cache. There is no need to process them into our application.
* Use sharding for storing data based on hash function into different storage. It means distributed our data base on a hash function, a simple hash function is, if the right bit of id is 0 route the data to cluster storage 0 and if is 1 route the data to cluster storage 1;
* The most important thing about Map is set the right size when create it, because there is no need to rehash and moved all buckets again; We can set the estimate size based on our real data statistics.
* Read the database data from slave/guard database ond use master just for inserting data. Also we can use Debezium for reading the database log inorder to completely separate read operation from database.
* Use a global MessageDto for communication with a Generic payload to cover every type of object and error. It means the client needs always consume one type of object and get the data from its payload. For the sake of simplicity, used text plane for returning the error response and batchId.
* The constant numbers in the application should set from the statistics data that is extracted from production environment.
* Using [toxiproxy](https://github.com/Shopify/toxiproxy) for simulating network conditions. Toxiproxy is the tool you need to prove with tests that your application doesn't have single points of failure.


## Conclusion
* If we have more item per chunk we will have a little bit better throughput.
* When the database updater worker thread increased to 5, the performance increased. If you have more write, you should increase the number of this worker thread.
* When the number of concurrent producers increased to 100, the performance increased by 60%, in the same condition.
