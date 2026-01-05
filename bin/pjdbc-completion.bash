# Bash completion for PJDBC CLI
#
# Installation:
#   source /path/to/pjdbc-completion.bash
#
# Or add to ~/.bashrc:
#   source /path/to/pjdbc-completion.bash
#
# Or copy to /etc/bash_completion.d/pjdbc

_pjdbc_completions() {
    local cur prev words cword
    _init_completion || return

    # Main commands
    local commands="validate list show chain test help version"

    # Driver prefixes for 'show' command
    local drivers="audit cat chaos circuitbreaker federate filter log mask
                   mock null readonly retry schema sink tee timeout usermap void"

    case "$prev" in
        pjdbc)
            COMPREPLY=($(compgen -W "$commands --help --version" -- "$cur"))
            return
            ;;
        show)
            COMPREPLY=($(compgen -W "$drivers" -- "$cur"))
            return
            ;;
        validate|chain|test)
            # Suggest starting a JDBC URL
            if [[ -z "$cur" ]]; then
                COMPREPLY=("jdbc:")
            elif [[ "$cur" == jdbc:* ]]; then
                # Suggest PJDBC driver prefixes after jdbc:
                local prefix="${cur#jdbc:}"
                if [[ -z "$prefix" ]]; then
                    local suggestions=""
                    for d in $drivers; do
                        suggestions="$suggestions jdbc:$d:"
                    done
                    COMPREPLY=($(compgen -W "$suggestions" -- "$cur"))
                fi
            fi
            return
            ;;
        --help|-h|help|--version|-v|version|list)
            # No further completions
            return
            ;;
    esac

    # Default: complete commands
    COMPREPLY=($(compgen -W "$commands" -- "$cur"))
}

# Register completion
complete -F _pjdbc_completions pjdbc
