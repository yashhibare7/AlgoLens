/**
 * The code the editor starts with.
 *
 * <p>Kept here rather than fetched so the visualizer is usable the instant the page paints, even
 * before `/api/problems` answers -- and so a first-time visitor's very first action can be
 * pressing Run. Everything else in the library comes from the backend.
 */
export const DEFAULT_SAMPLE = `// Press Run, then step through with the arrow keys.
int[] arr = {5, 2, 8, 1, 9, 3};

for (int i = 0; i < arr.length - 1; i++) {
    for (int j = 0; j < arr.length - i - 1; j++) {
        if (arr[j] > arr[j + 1]) {
            int temp = arr[j];
            arr[j] = arr[j + 1];
            arr[j + 1] = temp;
        }
    }
}

System.out.println("Sorted: " + arr);
`;
