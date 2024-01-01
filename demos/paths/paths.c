#include <stdio.h>
#include <stdlib.h>

void floyd_warshall(int **matrix, size_t n)
{
	int tmp;
	for (size_t k = 0; k < n; ++k) {
		for (size_t i = 0; i < n; ++i) {
			for (size_t j = 0; j < n; ++j) {
				if (matrix[i][k] != -1 && matrix[k][j] != -1) {
					tmp = matrix[i][k] + matrix[k][j];
					if (matrix[i][j] == -1 || matrix[i][j] > tmp)
						matrix[i][j] = tmp;
				}
			}
		}
	}

}

int main(void) {
	size_t n;
	int **matrix;

	scanf("%lu", &n);
	matrix = malloc(sizeof(*matrix) * n);
	for (size_t i = 0; i < n; ++i) {
		matrix[i] = malloc(sizeof(*matrix[i]) * n);
		for (size_t j = 0; j < n; ++j) {
			scanf("%d", matrix[i] + j);
		}
	}
	floyd_warshall(matrix, n);
	for (size_t i = 0; i < n; ++i) {
		for (size_t j = 0; j < n; ++j) {
			printf("%d ", matrix[i][j]);
		}
		printf("\n");
		free(matrix[i]);
	}
	free(matrix);
	return 0;
}
