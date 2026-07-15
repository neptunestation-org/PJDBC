## 2026-07-15 - SQL Filter Bypass via Comments
**Vulnerability:** SQL security filters (Readonly, SchemaValidation) could be bypassed by using SQL comments (/*...*/ or --...) instead of whitespace to separate keywords.
**Learning:** Standard regex anchors like \s+ or ^\s* are insufficient for SQL parsing as databases treat comments as whitespace.
**Prevention:** Use a robust PREFIX/SEP regex that explicitly matches both whitespace and all supported SQL comment styles, combined with Pattern.DOTALL for multi-line support.
