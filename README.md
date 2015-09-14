Elasticsearch-synonym-remote
=======================

## Overview

Elasticsearch Synonym Remote Plugin provides file_remote_synonym filter

## Version

| Version   | elasticsearch |
|:---------:|:-------------:|
| master    | 1.7.1         |
| 1.0.0     | 1.7.1         |


## Installation

    $ $ES_HOME/bin/plugin --install github.com/jianfengye/elasticsearch-analysis-synonym/1.5.0

## Getting Started

### Create synonym.txt File in Remote

For example, the url is "http://test.com/synonyms.txt"

make sure the request accept "Last-Modified" header.

make sure text follow the synonyms file's format.

### Set elasticsearch.yml

For Example:

```
index:
  analysis:
    analyzer:
      test_synonym:
          tokenizer: whitespace
          filter: [my_synonym_filter]
    filter:
      my_synonym_filter:
          type: file_remote_synonym  ## this type specify file_remote_synonym filter
          reload_interval: 10  ## this value show how long elasticsearch will refresh the remote_synonyms_path
          remote_synonyms_path: "http://test.com/synonym.txt"  ## specify the remote_synonyms_path
```

### Reload Synonyms

* update remote_synonyms_path file, and update it's Last-Modified
* close Index
* open Index
