#!/bin/bash
set -e

# Files created by Elasticsearch should always be group writable too
umask 0002

# Allow user specify custom CMD, maybe bin/elasticsearch itself
# for example to directly specify `-E` style parameters for elasticsearch on k8s
# or simply to run /bin/bash to check the image
if [[ "$1" == "eswrapper" || $(basename "$1") == "elasticsearch" ]]; then
  # Rewrite CMD args to remove the explicit command,
  # so that we are backwards compatible with the docs
  # from the previous Elasticsearch versions < 6
  # and configuration option:
  # https://www.elastic.co/guide/en/elasticsearch/reference/5.6/docker.html#_d_override_the_image_8217_s_default_ulink_url_https_docs_docker_com_engine_reference_run_cmd_default_command_or_options_cmd_ulink
  # Without this, user could specify `elasticsearch -E x.y=z` but
  # `bin/elasticsearch -E x.y=z` would not work. In any case,
  # we want to continue through this script, and not exec early.
  set -- "${@:2}"
else
  # Run whatever command the user wanted
  exec "$@"
fi

# Allow environment variables to be set by creating a file with the
# contents, and setting an environment variable with the suffix _FILE to
# point to it. This can be used to provide secrets to a container, without
# the values being specified explicitly when running the container.
#
# This is also sourced in elasticsearch-env, and is only needed here
# as well because we use ELASTIC_PASSWORD below. Sourcing this script
# is idempotent.
source /usr/share/elasticsearch/bin/elasticsearch-env-from-file

setup_keystore() {
  if [[ -f bin/elasticsearch-users ]]; then
    # Check for the ELASTIC_PASSWORD environment variable to set the
    # bootstrap password for Security.
    #
    # This is only required for the first node in a cluster with Security
    # enabled, but we have no way of knowing which node we are yet. We'll just
    # honor the variable if it's present.
    if [[ -n "$ELASTIC_PASSWORD" ]]; then
      [[ -f /usr/share/elasticsearch/config/elasticsearch.keystore ]] || (elasticsearch-keystore create)
      if ! (elasticsearch-keystore has-passwd --silent) ; then
        # keystore is unencrypted
        if ! (elasticsearch-keystore list | grep -q '^bootstrap.password$'); then
          (echo "$ELASTIC_PASSWORD" | elasticsearch-keystore add -x 'bootstrap.password')
        fi
      else
        # keystore requires password
        if ! (echo "$KEYSTORE_PASSWORD" \
            | elasticsearch-keystore list | grep -q '^bootstrap.password$') ; then
          COMMANDS="$(printf "%s\n%s" "$KEYSTORE_PASSWORD" "$ELASTIC_PASSWORD")"
          (echo "$COMMANDS" | elasticsearch-keystore add -x 'bootstrap.password')
        fi
      fi
    fi
  fi
}

configure_logging() {
  if [[ -n "$ES_LOG_STYLE" ]]; then
    case "$ES_LOG_STYLE" in
      console)
        # This is the default. Nothing to do.
        ;;
      file)
        # Overwrite the default config with the stack config
        mv /usr/share/elasticsearch/config/log4j2.file.properties /usr/share/elasticsearch/config/log4j2.properties
        ;;
      *)
        echo "ERROR: ES_LOG_STYLE set to [$ES_LOG_STYLE]. Expected [console] or [file]" >&2
        exit 1 ;;
    esac
  fi
}

run_init_scripts() {
  if [[ -d /docker-entrypoint-initdb.d ]]; then
    for init_file in /docker-entrypoint-init.d/* ; do
      if [[ ! -f "$init_file" ]]; then
        echo "$0: ignoring $init_file"
        continue
      fi

      case "$init_file" in
        *.sh)
          if [[ -x "$init_file" ]]; then
            echo "$0: running $init_file"
            "$init_file"
          else
            echo "$0: sourcing $init_file"
            source "$init_file"
          fi
          ;;

        *)
          echo "$0: ignoring $init_file"
          ;;

      esac
    done
  fi
}

setup_keystore
configure_logging
run_init_scripts

# Signal forwarding and child reaping is handled by `tini`, which is the
# actual entrypoint of the container
exec /usr/share/elasticsearch/bin/elasticsearch <<<"$KEYSTORE_PASSWORD"
