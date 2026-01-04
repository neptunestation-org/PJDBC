Use 'bd' for task tracking

## Test Output Filtering

When running tests and filtering output multiple ways, use `tee` with process substitution to avoid running tests multiple times:

```bash
mvn test 2>&1 | tee >(grep "BUILD") >(grep "DriverTest") >(grep -E "Failures: [1-9]") >/dev/null
```

This runs tests once and filters the output three different ways in parallel.
