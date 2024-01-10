.PHONY: clean

default: strings

strings: strings.ll libstring.ll ../format.ll
	clang -o $@ $^

clean:
	rm -f strings strings.ll libstring.ll
