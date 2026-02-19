## 2026-02-19 - [Alias-based Masking Bypass]
**Vulnerability:** The `DataMaskingDriver` was vulnerable to a masking bypass when columns were aliased in SQL queries. The masking check was performed on the provided column label rather than the underlying column index.
**Learning:** Masking proxy drivers must resolve column labels to their underlying indices as early as possible and use the index-based state for all subsequent checks to ensure consistency across all getter overloads.
**Prevention:** Always delegate label-based getter methods to their index-based counterparts in proxy drivers.
