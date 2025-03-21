import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.*;
import org.apache.spark.util.sketch.CountMinSketch;

public class CountMinSketchExample {
    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
                .appName("CountMinSketchExample")
                .master("local[*]") // Utilize all CPU cores
                .getOrCreate();

        // Define schema to ensure correct data types
        StructType schema = new StructType(new StructField[]{
            DataTypes.createStructField("userId", DataTypes.IntegerType, false),
            DataTypes.createStructField("songId", DataTypes.LongType, false), // Change IntegerType → LongType
            DataTypes.createStructField("rating", DataTypes.IntegerType, true),
        });

        // Load dataset
        Dataset<Row> songData = spark.read()
                .schema(schema)
                .csv(args[0])  // Load file passed as command-line argument
                .select("songId")
                .cache(); // Cache for performance

        // Create a Count-Min Sketch with width=1000, depth=5, and a seed for reproducibility
        CountMinSketch sketch = CountMinSketch.create(1000, 5, 1234);

        // Process dataset and update Count-Min Sketch
        JavaRDD<Long> songIds = songData.toJavaRDD().map(row -> row.getLong(0)); // Now safely retrieves Long values
        songIds.foreach(sketch::add);

        // Estimate the frequency of a specific song
        long songIdToEstimate = 26470;  // Change this to any songId you want to estimate
        long estimate = sketch.estimateCount(songIdToEstimate);
        System.out.println("Estimated number of plays for songId " + songIdToEstimate + ": " + estimate);

        // Stop Spark session
        spark.stop();
    }
}





        
//         songData.show(10);  // Debugging: Verify correct songId values
//         System.out.println("display first 10 rows");
    
//         // Create a Count-Min Sketch
//         double relativeError = 0.0001; // Epsilon
//         double confidence = 0.99; // Delta
//         int seed = 42; // Random seed
//         CountMinSketch cms = CountMinSketch.create(relativeError, confidence, seed);
        
//         // Efficient parallel update using foreachPartition
//         songData.javaRDD().foreachPartition(partition -> {
//             CountMinSketch localCms = CountMinSketch.create(relativeError, confidence, seed);
//             int count = 0; // Counter to keep track of the number of song IDs processed
//             while (partition.hasNext() && count < 50) { //ik wil niet 9999999 lines printen
//                 Row row = partition.next();
//                 int songId = row.getInt(0); // Retrieve value of first column, which is songId
//                 System.out.println("Processing songId: " + songId);  // Debugging print
//                 localCms.addLong(songId, 1);
//                 count++;
//                 System.out.println("Total count in sketch: " +"song id: " + songId + "count: " + localCms.totalCount());
// }
//             synchronized (localCms) { // Avoid race conditions
//                 localCms.mergeInPlace(cms);

//                 System.out.println("Merging local sketch into global sketch");
                
//             }

//         });

//         System.out.println("Total count in sketch: " + cms.totalCount());


//         // Estimate the count for a specific song ID
//         long testSongId = 44782;
//         long testSongIdd = 26470;
//         if (songData.filter("songId = " + testSongId).count() == 0) {
//             System.out.println("WARNING: Song ID " + testSongId + " does not exist in dataset!");
//         }
//         long estimatedCount = cms.estimateCount(testSongId);
//         System.out.println("Estimated count for song " + testSongId + ": " + estimatedCount);
//         System.out.println(" Checking estimated counts for all processed song IDs:");
        
//         System.out.println("Estimated count for song " + testSongIdd + ": " + cms.estimateCount(testSongIdd));
        

//         // Compute actual counts using Spark SQL
//         Dataset<Row> actualCounts = songData
//                 .groupBy("songId")
//                 .count()
//                 .filter("songId = 44782");

//         System.out.println("Actual count for song " + testSongId + ":");
//         actualCounts.show();
        

//         spark.stop();
//     }
// }