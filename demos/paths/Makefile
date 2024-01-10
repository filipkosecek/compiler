.PHONY: clean

default: paths

paths: paths.ll ../format.ll
	clang -o $@ $^

clean:
	rm -f paths paths.ll
