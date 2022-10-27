import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LinksSuggester {

    List<Suggest> suggestList = new ArrayList<>();


    //конструктор
    public LinksSuggester(File file) throws IOException, WrongLinksFormatException {

        //проверяем что файл существует
        if (file.isFile()) {
            //System.out.println("Файл конфига существует");
            FileReader fileReader = new FileReader(file);
            BufferedReader reader = new BufferedReader(fileReader);
            //считываем первую строку
            String line = reader.readLine();
            //цикл чтения файла
            //вычитываем построчно файл и проверяем каждую строку, что она состоит из трех частей
            while (line != null) {
                //System.out.println("Обрабатываем строку из конфига");
                //System.out.println(line);
                //проверка что строка конфига состоит из трех частей разделенных табуляцией
                if (line.split("\t").length == 3) {
                    //System.out.println("Строка прошла проверку");
                    //добавили строку конфига после проверки в список
                    suggest(line);
//
//                    for (String word : line.split("\t")) {
//                        System.out.print(word + " ! ");
//                    }
                } else {
                    System.out.println("Строка не прошла проверку, нужно прекратить выполнение обработки");
                    throw new WrongLinksFormatException("Неправильный формат строки в конфиге, меньше трех позиций в строке");
                }
                //System.out.println();
                //переход на следующую строку
                line = reader.readLine();
            }
        } else {
            System.out.println("Невозможно прочитать файл");
        }

        //finally{ try { if (reader != null) reader.close(); } catch (IOException e) { e.printStackTrace(); } }

    }

    //список слов для ссылок в документе
    public List<Suggest> suggest(String text) {
        String[] suggestRow = text.split("\t");
        Suggest incomeSuggest = new Suggest(suggestRow[0], suggestRow[1], suggestRow[2]);
        suggestList.add(incomeSuggest);
        return suggestList;
    }


}
