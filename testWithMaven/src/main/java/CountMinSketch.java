import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Row;
import java.io.Serializable;
import java.util.Random;
import java.util.HashSet;
import java.util.stream.IntStream;

public class CountMinSketch implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int[][] sketch;
    private final int width;
    private final int depth;
    private final int[] hashA;
    private final int[] hashB;

    public CountMinSketch(int width, int depth) {
        this.width = width;
        this.depth = depth;
        this.sketch = new int[depth][width];
        this.hashA = new int[depth];
        this.hashB = new int[depth];

        Random rand = new Random();
        for (int i = 0; i < depth; i++) {
            hashA[i] = rand.nextInt(Integer.MAX_VALUE);
            hashB[i] = rand.nextInt(Integer.MAX_VALUE);
        }
    }

    private int hash(int i, int key) {
        int prime = 31;  // Prime for better spreading
        return Math.floorMod((hashA[i] * key + hashB[i] * prime), width);
    }

    public void update(int key, int count) {
        for (int i = 0; i < depth; i++) {
            int index = hash(i, key);
            sketch[i][index] += count;
        }
    }

    public int estimate(int key) {
        return IntStream.range(0, depth)
                .map(i -> sketch[i][hash(i, key)])
                .min()
                .orElse(0);
    }

    public void merge(CountMinSketch other) {
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                this.sketch[i][j] = Math.min(this.sketch[i][j], other.sketch[i][j]);  // Use min instead of sum
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: CountMinSketch <file>");
            System.exit(1);
        }

        SparkSession spark = SparkSession.builder()
                .appName("CountMinSketch")
                .master("local[*]")
                .getOrCreate();

        JavaRDD<String> lines = spark.read().textFile(args[0]).javaRDD();

        System.out.println(" First few lines of the input data:");
        lines.take(10).forEach(System.out::println);

        int width = 10000;  // Reduce hash collisions
        int depth = 8;      // Optimize depth

        JavaRDD<CountMinSketch> sketches = lines.mapPartitions(partition -> {
            CountMinSketch cms = new CountMinSketch(width, depth);
            HashSet<Integer> seenSongIds = new HashSet<>();
            partition.forEachRemaining(line -> {
                String[] parts = line.split("\\s*,\\s*");
                try {
                    int songId = Integer.parseInt(parts[1]);
                    if (!seenSongIds.contains(songId)) {
                        cms.update(songId, 1);
                        seenSongIds.add(songId);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Skipping invalid line: " + line);
                }
            });
            return java.util.Collections.singletonList(cms).iterator();
        });

        CountMinSketch finalSketch = sketches.reduce((cms1, cms2) -> {
            cms1.merge(cms2);
            return cms1;
        });

        int testSongId = 44782;
        int estimatedCount = finalSketch.estimate(testSongId);
        System.out.println("Estimated count for song " + testSongId + " = " + estimatedCount);

        Dataset<Row> actualCounts = spark.read().csv(args[0])
                .withColumnRenamed("_c1", "songId")
                .selectExpr("cast(songId as int) as songId")
                .groupBy("songId")
                .count()
                .filter("songId = 44782");

        System.out.println("Actual count for song " + testSongId + ":");
        actualCounts.show();

        spark.stop();
    }
}
