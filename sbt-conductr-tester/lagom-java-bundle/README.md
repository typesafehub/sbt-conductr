## Lagom Java Bundle tester

This tester provides a lagom service and a play app acting as a client.

When installed you should be able to run:

```

# query the lagom service directly
$  curl http://192.168.10.1:9000/foo
hardcoded-foo-response

# query the play service directly
$  curl http://192.168.10.1:9000/
Hello world

# query the play service (this causes a downstream call to the lagom service which is located via service locator)
$  curl http://192.168.10.1:9000/lagom-redirect
via-play hardcoded-foo-response

```
