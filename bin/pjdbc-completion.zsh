#compdef pjdbc
#
# Zsh completion for PJDBC CLI
#
# Installation:
#   1. Copy to a directory in your $fpath (e.g., ~/.zsh/completions/)
#   2. Rename to _pjdbc
#   3. Run: autoload -Uz compinit && compinit
#
# Or source directly:
#   source /path/to/pjdbc-completion.zsh

_pjdbc() {
    local -a commands drivers

    commands=(
        'validate:Parse and validate a PJDBC URL'
        'list:List all available drivers'
        'show:Show details for a specific driver'
        'chain:Show the driver chain for a URL'
        'test:Test a database connection'
        'help:Show help message'
        'version:Show version information'
    )

    drivers=(
        'audit:SQL statement auditing'
        'cat:Pass-through driver (no-op)'
        'chaos:Failure injection for testing'
        'circuitbreaker:Circuit breaker pattern'
        'federate:Query multiple databases'
        'filter:SQL transformation'
        'log:SQL logging'
        'mask:Data masking'
        'mock:Mock database for testing'
        'null:Null driver'
        'readonly:Enforce read-only access'
        'retry:Automatic retry on failure'
        'schema:Schema validation'
        'sink:Discard all operations'
        'tee:Write replication'
        'timeout:Query timeout enforcement'
        'usermap:Credential mapping'
        'void:Void driver'
    )

    case "$words[2]" in
        show)
            _describe -t drivers 'driver' drivers
            ;;
        validate|chain|test)
            _message 'JDBC URL (e.g., jdbc:retry:jdbc:postgresql://localhost/db)'
            ;;
        *)
            if (( CURRENT == 2 )); then
                _describe -t commands 'command' commands
            fi
            ;;
    esac
}

_pjdbc "$@"
