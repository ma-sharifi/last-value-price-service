# Improvement

* Use Back pressure mechanism. Back pressure can help by limiting the queue size (user ArrayBlockingQueue for storage updater with a fixed size) and PriceData Cache(Each entry is belong to one batch, we can fix the size to 100 batch ), thereby maintaining a high throughput rate and good response times for jobs already in the queue. Once the queue/cache fills up, clients get a server busy or HTTP 503 status code to try again later.
* Using Redis/Kafka as publisher/subscriber
* Use redis as a cache to save our data when we put them in the StorageQueue in order to know which records is waiting to persis into storage.
* Use Grid computing tools for processing priceData and compare them with Instrument data and send them Queue and Cache. There is no need to process them into our application.
* Use sharding for storing data based on hash function into different storage. It means distributed our data base on a hash function, a simple hash function is, if the right bit of id is 0 route the data to cluster storage 0 and if is 1 route the data to cluster storage 1;
* The most important thing about Map is set the right size when create it, because there is no need to rehash and moved all buckets again; We can set the estimate size based on our real data statistics.
* Read the database data from slave/guard database ond use master just for inserting data. Also we can use Debezium for reading the database log inorder to completely separate read operation from database.
* Use a unique message for communication with Generic payload to cover every type of objects and error. It means client needs always consume one type of object and get the data from its payload. For the sake of simplicity used text plane for returning the error response and batchId.
* The constant numbers in the application should set from the statistics data that is extracted from production environment.
* Using [toxiproxy](https://github.com/Shopify/toxiproxy) for simulating network conditions. Toxiproxy is the tool you need to prove with tests that your application doesn't have single points of failure.




