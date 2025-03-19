import org.apache.spark.util.sketch.CountMinSketch;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import org.apache.spark.sql.types.*;

public class CountMinSketchExample {
    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("CountMinSketchExample")
                .master("local[*]") // Utilize all CPU cores
                .getOrCreate();


           // (A): Dataset

        // We define a schema so that Spark doesn't dynamically tries to interpret the
        // data upon loading, reducing overhead
        StructType schema = new StructType(new StructField[]{
            DataTypes.createStructField("userId", DataTypes.IntegerType, false),
            DataTypes.createStructField("songId", DataTypes.IntegerType, false),
            DataTypes.createStructField("rating", DataTypes.IntegerType, true),
        });
        
        Dataset<Row> songData = spark.read()
                .schema(schema)  // Force correct column types
                .csv(args[0])
                .select("songId")  // Keep only the songId column
                .cache();  // Cache the dataset to improve performance
        
        songData.show(10);  // Debugging: Verify correct songId values
        System.out.println("display first 10 rows");
    
        // Create a Count-Min Sketch
        double relativeError = 0.0001; // Epsilon
        double confidence = 0.99; // Delta
        int seed = 42; // Random seed
        CountMinSketch cms = CountMinSketch.create(relativeError, confidence, seed);

        // Efficient parallel update using foreachPartition
        songData.javaRDD().foreachPartition(partition -> {
            CountMinSketch localCms = CountMinSketch.create(relativeError, confidence, seed);
            int count = 0; // Counter to keep track of the number of song IDs processed
            while (partition.hasNext() && count < 50) { //ik wil niet 9999999 lines printen
                Row row = partition.next();
                int songId = row.getInt(0); // Retrieve value of first column, which is songId
                System.out.println("Processing songId: " + songId);  // Debugging print
                localCms.addLong(songId, 1);
                count++;
}
            synchronized (cms) { // Avoid race conditions
                cms.mergeInPlace(localCms);
                System.out.println("Merging local sketch into global sketch");
            }
        });

        // Estimate the count for a specific song ID
        long testSongId = 44782;
        long testSongIdd = 26470;
        if (songData.filter("songId = " + testSongId).count() == 0) {
            System.out.println("WARNING: Song ID " + testSongId + " does not exist in dataset!");
        }
        long estimatedCount = cms.estimateCount(testSongId);
        System.out.println("Estimated count for song " + testSongId + ": " + estimatedCount);
        System.out.println(" Checking estimated counts for all processed song IDs:");
        
        System.out.println("Estimated count for song " + testSongIdd + ": " + cms.estimateCount(testSongIdd));
        

        // Compute actual counts using Spark SQL
        Dataset<Row> actualCounts = songData
                .groupBy("songId")
                .count()
                .filter("songId = 44782");

        System.out.println("Actual count for song " + testSongId + ":");
        actualCounts.show();
        

        spark.stop();
    }
}
