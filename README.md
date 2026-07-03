
# senior-accounting-officer-stubs

This is a placeholder README.md for a new repository

## Running tests with Bruno

Users can import the collection into Bruno and, once the required environment setup is complete, run the protected service requests directly.

### What is Bruno?

Bruno is an open-source API client used for manual exploratory testing and cross-team collaboration. It allows you to create, manage and run HTTP requests against a service directly from a collection stored in a repository.

### (HMRC) MDTP Recommendation

Bruno is the recommended API client tool for use on the MDTP platform, as detailed in the [MDTP Handbook](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/mdtp-test-approach/acceptance-testing/api-testing/index.html)

"MDTP recommends the Bruno API client tool for manual exploratory testing and cross-team collaboration. Only tools that have been reviewed and approved by Platform Security are permitted for use."

### Pre-requisites:
* Download and install Bruno.
* Start the needed services in a terminal session with the following command:

```bash
sm2 --start SAO_ALL
```

### Running Tests in the Local environment

* Open Bruno.
* From the Bruno menu select Open Collection.
* Navigate to the bruno folder in the repository.
* Click the Open button.
* In Bruno, select the local environment from the environment dropdown.
* Select a request from the collection in the left-hand panel.
* Click Send to execute the request.
* Verify the response status and body match the expected output.


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").