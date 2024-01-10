.PHONY: clean

default: max

max: max.ll ../format.ll
	clang -o $@ $^

clean:
	rm -f max max.ll
