# 2AMD15-group-6

Om dit werkend te krijgen, moet je op je windows laptop ook Winutils hebben geinstalleerd onder je HADOOP_HOME environment variable.
Hadoop 3.3.6 (laatste versie) werkt. Link: https://github.com/cdarlint/winutils

Ook moet je handmatig de plays.csv file in het mapje testWithMaven zetten, deze is te groot om te uploaden naar Github.

Nadat je de repo hebt geopend in vs code:
1. Run `cd .\testWithMaven\` 
2. Run `mvn clean package`
3. Run spark: `spark-submit --class "TestClassWordCount"  target/app.jar plays.csv`

Ergens tussen alle output die Spark genereert vind je: `Total number of lines: 10000000`

Ik krijg ook 3/4 errors aan het einde van de run, maar dit heeft allemaal te maken met het feit dat spark temporary files die die zelf heeft aangemaakt niet meer kan verwijderen. 
Als je naar de error kijkt kan je de file path vinden van die temporary files, die kan je na elke run zelf deleten
