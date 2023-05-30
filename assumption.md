### Assumptions when solving the challenge:

? clients which call the service while a batch is being processed.
? Does it need a REST endpoint? OR service is nought?
I I used caffein as a database
* Assumed client can get the during batch run

* Simplicity is more important than other things. Tried to have a small code.
* Storage here is a database that must persist data into the disk, it means storage is much slower than other data structure we used.
* Assumed there are latest price always in our storage.
* For sake of simplicity I did not define an new object as Payload, I assumed payload is price and nothing else!
* Because we always check the instrument date with price data date and for them the price date is after instrument date we must update, I take this application read bounded.