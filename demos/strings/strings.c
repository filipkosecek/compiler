#include <stdio.h>
#include <string.h>
#include <stdlib.h>

void sort(char **strings, size_t string_count, size_t max_size)
{
	char *tmpstring;

	tmpstring = malloc(sizeof(*tmpstring) * max_size);
	for (unsigned int i = 0; i < string_count; ++i) {
		for (unsigned int j = 0; j < string_count - i - 1; ++j) {
			if (strcmp(strings[j], strings[j+1]) > 0) {
				strcpy(tmpstring, strings[j]);
				strcpy(strings[j], strings[j+1]);
				strcpy(strings[j+1], tmpstring);
			}
		}
	}

	free(tmpstring);
}

int main(void)
{
	size_t string_count, max_size;
	char **strings;

	scanf("%lu", &string_count);
	scanf("%lu", &max_size);
	strings = malloc(sizeof(*strings) * string_count);
	for (unsigned int i = 0; i < string_count; ++i) {
		strings[i] = malloc(sizeof(*strings[i]) * (max_size + 1));
		scanf("%s", strings[i]);
	}
	sort(strings, string_count, max_size);
	puts("---------------------");
	for (unsigned int i = 0; i < string_count; ++i) {
		puts(strings[i]);
		free(strings[i]);
	}
	free(strings);
	return 0;
}
