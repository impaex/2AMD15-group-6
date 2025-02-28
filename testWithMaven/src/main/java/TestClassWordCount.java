import scala.Tuple2;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Dataset;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.event.SwingPropertyChangeSupport;

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

        System.out.println("Total number of lines in RDD: " + lineCount);
        System.out.println("Total number of rows in dataset: " + rowCount);

        /*
         * Question 2: Finding highest average rating among everyone who left at least
         * 10 ratings
         */
        // Filter all lines without rating
        JavaRDD<String> ratingsOnly = lines.filter(s -> s.split(",").length == 3);
        // Map all lines with rating to following pair format: (userid, (rating, 1))
        JavaPairRDD<String, Tuple2<Integer, Integer>> ratingPairs = ratingsOnly.mapToPair(x -> {
            String[] parts = x.split(",");
            Integer rating = Integer.parseInt(parts[2].trim());
            return new Tuple2<>(parts[0], new Tuple2<>(rating, 1));
        });

        JavaPairRDD<String, Tuple2<Integer, Integer>> hallo = ratingPairs
                .reduceByKey((pair1, pair2) -> new Tuple2<>(pair1._1() + pair2._1(), pair1._2() + pair2._2()));

        JavaPairRDD<String, Tuple2<Integer, Integer>> hallo1 = hallo.filter(s -> s._2()._2() >= 10);

        JavaPairRDD<String, Double> hallo2 = hallo1.mapToPair(x -> {
            return new Tuple2<>(x._1(), x._2()._1() / (double) x._2()._2());
        });

        System.out.println("Hoi" + hallo2.mapToPair(x -> x.swap()).sortByKey(false).mapToPair(x -> x.swap()).take(1));
        
        // System.out.println("Hoi" + hallo2.take(10));

        // JavaPairRDD<Integer, Integer> pairs = ratingsOnly.mapToPair(x ->
        // x.split(",")new Tuple2());
        // JavaRDD<String> csvSplit = ratingsOnly.flatMap(s ->
        // Arrays.asList(s.split(",")).iterator());
        // System.out.println("Hoi" + csvSplit.take(10));
        spark.stop();
    }
}
