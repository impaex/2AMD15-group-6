import scala.Tuple2;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Dataset;
import static org.apache.spark.sql.functions.col;
import org.apache.spark.sql.functions;
import java.io.Serializable;


import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.ml.recommendation.ALS;
import org.apache.spark.ml.recommendation.ALSModel;


import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.event.SwingPropertyChangeSupport;
import javax.xml.crypto.Data;

public final class TestClassWordCount {
    private static final Pattern SPACE = Pattern.compile(" ");

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.err.println("Usage: JavaWordCount <file>");
            System.exit(1);
        }

        SparkSession spark = SparkSession
                .builder()
                .appName("JavaWordCount")
                .master("local[*]")
                .getOrCreate();

        /*
         * Question 1: Loading the data
         */

        // (B): RDD
        JavaRDD<String> lines = spark.read().textFile(args[0]).javaRDD();

        long lineCount = lines.count();

        // (A): Dataset

        // We define a schema so that Spark doesn't dynamically tries to interpret the
        // data upon loading, reducing overhead
        StructType schema = new StructType(new StructField[] {
                DataTypes.createStructField("userId", DataTypes.IntegerType, false),
                DataTypes.createStructField("songid", DataTypes.IntegerType, false),
                DataTypes.createStructField("rating", DataTypes.IntegerType, true),
        });

        Dataset<Row> dataSet = spark.read().schema(schema).csv(args[0]);

        long rowCount = dataSet.count();

        // JavaRDD<String> words = lines.flatMap(s ->
        // Arrays.asList(SPACE.split(s)).iterator()).filter(s->s.startsWith("wi"));

        // JavaPairRDD<String, Integer> ones = words.mapToPair(s -> new Tuple2<>(s, 1));

        // JavaPairRDD<String, Integer> counts = ones.reduceByKey((i1, i2) -> i1 + i2);

        // List<Tuple2<String, Integer>> output = counts.collect();
        // for (Tuple2<?,?> tuple : output) {
        // System.out.println(tuple._1() + ": " + tuple._2());
        // }

        System.out.println("Q1 - Total number of lines in RDD: " + lineCount);
        System.out.println("Q1 - Total number of rows in dataset: " + rowCount);

        /*
         * Question 2: Finding highest average rating among everyone who left at least
         * 10 ratings
         */

        // Filter all lines without rating
        JavaRDD<String> ratingsOnly = lines.filter(s -> s.split(",").length == 3);
        
        // Map all lines with rating to following pair format: (userid, (rating, 1))
        JavaPairRDD<Integer, Tuple2<Integer, Integer>> ratingPairs = ratingsOnly.mapToPair(x -> {
            String[] parts = x.split(",");
            Integer rating = Integer.parseInt(parts[2].trim());
            return new Tuple2<>(Integer.parseInt(parts[0].trim()), new Tuple2<>(rating, 1));
        });

        // ReduceByKey to add up all ratings and occurences per userid
        JavaPairRDD<Integer, Tuple2<Integer, Integer>> summedRatingPairs = ratingPairs
                .reduceByKey((pair1, pair2) -> new Tuple2<>(pair1._1() + pair2._1(), pair1._2() + pair2._2()));

        // Filter out userids with less than 10 ratings
        JavaPairRDD<Integer, Tuple2<Integer, Integer>> usersWithEnoughRatings = summedRatingPairs.filter(s -> s._2()._2() >= 10);

        // Compute the average rating per userid
        JavaPairRDD<Integer, Double> avgRatings = usersWithEnoughRatings.mapToPair(x -> {
            return new Tuple2<>(x._1(), x._2()._1() / (double) x._2()._2());
        });

        // Sort and store/print the userid with the highest average rating
        Tuple2<Integer, Double> maxUser = avgRatings.reduce((a, b) -> {
            if (a._2 >= b._2) {
                return a;
            } else {
                return b;
            }
        });

        System.out.println("Q2 - " + maxUser);

        /*
         * Question 3: Finding highest average rating among everyone who left at least
         * 10 ratings, and if there are more than one, pick the one with the smallest ID
         * 
         * This question follows most of Q2's steps, which are reused here.
         */

         Tuple2<Integer, Double> maxUserWithSmallestId = avgRatings.reduce((a, b) -> {
            if (a._2 > b._2) {
                return a;
            } else if (a._2 < b._2) {
                return b;
            } else {
                return a._1 < b._1 ? a : b;  // If ratings are equal, pick the smaller userid
            }
        });

        System.out.println("Q3 - " + maxUserWithSmallestId);


        /*
         * Question 5: Recommending songs to users
         */

        /* Remove the rows (songs) without ratings */
        Dataset<Row> filteredDataSet = dataSet.filter(col("rating").isNotNull());

        /* Split the data in test and training set */
        Dataset<Row>[] splits = filteredDataSet.randomSplit(new double[]{0.8, 0.2});
        Dataset<Row> trainingData = splits[0];  // 80% of the data
        Dataset<Row> testData = splits[1];  // 20% of the data

        /* Train the ALS model */
        ALS als = new ALS()
            .setMaxIter(5)
            .setRegParam(0.01)
            .setUserCol("userId")
            .setItemCol("songid")
            .setRatingCol("rating");
        
        /* Fit the model */
        ALSModel model = als.fit(trainingData);

        /* Set the cold start strategy to drop to ensure we don't get NaN evaluation metrics */
        model.setColdStartStrategy("drop");
    
        /* Predict the ratings */
        Dataset<Row> predictions = model.transform(testData);

        /* Evaluate the model */
        RegressionEvaluator evaluator = new RegressionEvaluator()
            .setMetricName("rmse")
            .setLabelCol("rating")
            .setPredictionCol("prediction");    
        
        double rmse = evaluator.evaluate(predictions);
        
        System.out.println("Q5 - Root-mean-square error = " + rmse);
    
        /* Generate top 10 song recommendations for five users */
        Dataset<Row> users = filteredDataSet.select(als.getUserCol()).distinct().limit(5);
        Dataset<Row> userSubsetRecs = model.recommendForUserSubset(users, 10);
         /* print top 10 song recommendations for five users */
        System.out.println("Q5 - Top 10 song recommendations for five users:");
        userSubsetRecs.show(false);
        


        /*
         * Question 6: Find most frequently played song
         */

        Dataset<Row> songCounts = dataSet.groupBy("songid").count();
        long maxCount = songCounts.agg(functions.max("count")).first().getLong(0);
        
        // Filter songs with the maximum count
        Dataset<Row> mostPlayedSongs = songCounts.filter(functions.col("count").equalTo(maxCount));

        // Show results (search for "songid" in terminal)
        System.out.println("Q6 - search songid in terminal");
        mostPlayedSongs.show();

        spark.stop();
    }
}
