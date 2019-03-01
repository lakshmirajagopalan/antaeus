## Antaeus

__1. Billing Scheduler:__  
Every month a scheduler runs to bill all the pending invoices.
Billing is tried via the external payment provider on the invoices one by one. 

__2. Failures:__  
Since payments, multiple failures can occur including network and other domain related failures.
And each failure might need its own way of resolving.
In order to not complicate with this logic, decided to record the failures for manual reconciliation.

__3. Fault Tolerance:__  
Also there could be system failures while billing process is under way. 
Hence it requires to add audit trails for each stage in order to recover the failed transactions.

The ideal tool for this use case would be log structures or message queues. 
If the payment system was idempotent, then it doesn't matter if we retried the same billing again.
If however, the payment system was not idempotent, then we need to take more care regarding flushing every event to queue before committing the transaction and Kafka, etc being non-transactional will not be optimal in such use case.
Here we use the same database for audit trail i.e store the billing progress of each pending invoice.

__4. Payment Progress:__  
Progress of an invoice payment is available from the database and is visible via REST 

__5. Reconciliation of Failed Payments:__  
It is possible to collect a list of failed payments and query the failure reason for each attempt through REST.

__6. Retry Payment for an Invoice:__  
REST interface to requeue an invoice(failed) for payment. 

__7. Endpoints:__  
    To get the invoices by Payment Status - paged    
     ```GET /rest/v1/invoices/status/{:paymentstatus}```

   To get the failure reasons and timestamps of an invoice billing   
     ```GET /rest/v1/invoices/failed/{:invoiceId}```
 
   To force re queue an invoice for re billing   
     ```POST /rest/v1/invoices/231/requeue``` 


__Things considered but avoided due to time limitations__   

1. Multi threaded payment calls. (requires locks and synchronisation on the database).
2. Asynchronous payment calls. (lack of my knowledge on how it is accomplished in kotlin)
3. Multiple instances of the application (requires locks and synchronisation).
4. Automatic retries (every failure might require a different kind of intervention).
Did not write tests for the already existing features in the challenge.