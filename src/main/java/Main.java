import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {

        Map<String, Integer> wordsMap; //карта всех слов и где они встречались впервые в файле
        Map<Suggest, Integer> mapSuggests; // карта советов и страниц где они найдены
        Map<Integer, Integer> mapInjections; //карта <Страница, Кол-во вставок на ней>

        System.out.println("Инициализация списка советов для чтения, которые будут вставлены в файл");
        LinksSuggester linksSuggester = new LinksSuggester(new File("data/config")); // создаём конфиг

        // перебираем pdfs в data/pdfs
        var directoryPdfs = new File("data/pdfs");
        var directoryConvertedPdfs = new File("data/converted");
        //System.out.println("Вывод файлов из директории которые нужно обработать: " + directoryPdfs.getPath());
        try {
            for (var fileIn : directoryPdfs.listFiles()) {
                //System.out.println("Обработка входящего файла с именем " + fileIn.getName());
                // для каждой pdfs создаём новую в data/converted
                var readerPdf = new PdfReader(fileIn);
                var writerPdf = new PdfWriter(directoryConvertedPdfs + "\\" + fileIn.getName());
                var writerPdfTemp = new PdfWriter(directoryConvertedPdfs + "\\temp" + fileIn.getName());
                var document = new PdfDocument(readerPdf); // входящий документ для обработки -> документ для чтения
                var documentConverted = new PdfDocument(writerPdf); //документ для редактирования финальный -> документ для записи финальный
                var documentConvertedTemp = new PdfDocument(writerPdfTemp); //документ для редактирования временный ->

                for (int i = 1; i <= document.getNumberOfPages(); i++) {
                    documentConvertedTemp.addNewPage(i);//заполняем временный документ просто пустыми страницами
                }
                document.copyPagesTo(1, document.getNumberOfPages(), documentConverted); //заполняем финальный pdf всеми страницами из входящего документа

                // карта <Слово, Страница оригинального документа, где слово появилось впервые>
                //метод создания карты всех слов и их первого появления в документе
                wordsMap = mapWordsInDoc(document);

                // метод возвращает карту <Совет><Страница где найден ключевое слово>
                // показывает на какой странице найдено ключевое слово, совет нужно будет вставлять на следующую страницу
                mapSuggests = mapSuggestWordsAndPages(wordsMap, linksSuggester.suggestList);

                System.out.println("Карта советов и страниц для текущего документа " + fileIn.getName());
                for (Map.Entry<Suggest, Integer> entry : mapSuggests.entrySet()) {
                    System.out.println("Совет " + entry.getKey() + " страница " + entry.getValue());
                }

                //метод возвращает карту <Станица, Кол-во вставок на ней>
                //нам нужно определиться с количеством вставок на странице
                mapInjections = listOfInjectionPerPage(mapSuggests);

                //вставляем во временном документе на нужных местах советы согласно заданию
                insertingSuggestions(documentConvertedTemp, mapInjections, mapSuggests);

                //перед финальными шагами нам нужно закрыть наш входящий документ
                document.close(); //на данном этапе он больше не нужен
                documentConvertedTemp.close(); //после работы с этим временным файлом нам нужно открыть его на чтение, чтобы скопировать с него страницы

                //для того чтобы скопировать страницы из временного документа нам нужно открыть его на чтение
                var readerPdfTemp = new PdfReader(directoryConvertedPdfs + "\\temp" + fileIn.getName());
                var documentConvertedTempReading = new PdfDocument(readerPdfTemp); //документ для редактирования временный открыт на чтение

                //копируем подготовленные страницы из временного документа в финальный на свои места
                //вставка осуществляется с конца документа чтобы не отслеживать сдвиг страниц, если делать это с начала документа
                insertPreparedPages(documentConvertedTempReading, documentConverted, mapInjections);

                //удаление временного файла
                Path path = Path.of(directoryConvertedPdfs + "\\temp" + fileIn.getName());
                Files.delete(path);
            }
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("Файл не найден");
        }
    }

    //методы

    //метод создания карты всех слов и их первого появления в документе
    //мы ищем слова в документе начиная перебирать его с последней страницы,
    //чтобы получить в итоге первое появление слова в документе
    // карта <Слово, Страница оригинального документа где оно появилось впервые>
    public static Map<String, Integer> mapWordsInDoc(PdfDocument document) {
        Map<String, Integer> result = new HashMap<>();
        //    System.out.println("Формируем карту слов из файла c конца документа в начало");
        for (int i = document.getNumberOfPages(); i >= 1; i--) {
            //        System.out.println("Обработка страницы " + i + " из " + document.getNumberOfPages());
            //получить текст со страницы
            var text = PdfTextExtractor.getTextFromPage(document.getPage(i));
            //ищем на странице в словах наши ключевые слова для советов
            for (String word : text.split(" ")) {
                //кладем слова с конца файла в словарь <СЛОВО> и <Страница> где нашли в первый раз
                result.put(word.toLowerCase().trim(), i);
            }
        }
        //System.out.println(result.containsKey("выделим"));
        //System.out.println();
        //document.close();
        return result;
    }

    // метод возвращает карту <Слово><Страница появления ключевого слова>
    // показывает на какой странице найдено ключевое слово, совет нужно будет вставлять на следующую страницу
    public static Map<Suggest, Integer> mapSuggestWordsAndPages(Map<String, Integer> mapAllWords, List<Suggest> suggestsList) {
        Map<Suggest, Integer> result = new HashMap<>();
        for (Suggest suggest : suggestsList) { //перебираем все советы и ищем совпадения в карте слов, берем в ней страницу и сохраняем в map
            //    System.out.println("Ищем ключевое слово " + suggest.getKeyWord() + "  в карте всех слов документа");
            if (mapAllWords.containsKey(suggest.getKeyWord())) {
                int pageNumber = mapAllWords.get(suggest.getKeyWord()).intValue();
                System.out.println("Ключевое слово " + suggest.getKeyWord() + " найдено на странице " + pageNumber);
                result.put(suggest, pageNumber);
            } else {
                System.out.println("Ключевое слово " + suggest.getKeyWord() + " не найдено в документе");
            }
        }


        return result;
    }

    //метод возвращает карту <Страница, Кол-во вставок на эту страницу>
    // карта со страницами и количеством вставок
    public static Map<Integer, Integer> listOfInjectionPerPage(Map<Suggest, Integer> suggestedPages) {
        //List<Integer> result = new ArrayList<>();
        List<Integer> listValues = new ArrayList<>(suggestedPages.values());
        Map<Integer, Integer> result = new HashMap<>(); //отсортированная map

        //System.out.println("Размер карты советов со страницами " + suggestedPages.size());
        Integer maxPage = Collections.max(suggestedPages.values());
        //System.out.println("Самая последняя страница для вставки " + maxPage); //дальше этой страницы вставки не будет

        //получаем список с уникальными страницами (без повторов) чтобы в дальнейшем посчитать кол-во вставок советов на них
        List<Integer> uniquePages = listValues.stream().distinct().collect(Collectors.toList());
//        for (int i = 0; i < uniquePages.size(); i++) {
//            System.out.println("Уникальные страница  " + uniquePages.get(i));
//        }
        //System.out.println("Ищем количество повторов страниц в " + listValues);
        int count = 0;
        for (int i = 0; i < uniquePages.size(); i++) {
            //    System.out.println(uniquePages.get(i) + " уникальная страница, ищем для нее повторы");
            for (int j = 0; j < listValues.size(); j++) {
                //        System.out.println("сравниваем " + uniquePages.get(i) + " = " + listValues.get(j));
                if (uniquePages.get(i) == listValues.get(j)) {
                    //            System.out.println("совпало");
                    count++;
                }
            }
            //System.out.println("Страница для вставки " + uniquePages.get(i) + " встречается " + count + " раз");
            result.put(uniquePages.get(i), count);
            count = 0;
        }
        //System.out.println("Формирование map с количеством вставок закончено");
        //System.out.println(result);
        return result;
    }

    //вставляем во временном документе на нужных местах советы согласно заданию
    //этим методом мы подготавливаем себе почву для следующего шага, чтобы подготовленные страницы скопировать в финальный документ
    public static void insertingSuggestions(PdfDocument documentConvertedTemp, Map<Integer, Integer> mapInjections, Map<Suggest, Integer> mapSuggests) {

        Set<Integer> pagesForInjecting = new HashSet<>(mapInjections.keySet());
        //System.out.println("Идем по страницам где нужно вставлять советы во временном документе " + pagesForInjecting);
        for (Integer page : pagesForInjecting) {
            //System.out.println("Вставляем на странице " + page + " кол-во советов " + mapInjections.get(page));
            // for (int i = 1; i <= mapInjections.get(page); i++) {
            var rect = new Rectangle(documentConvertedTemp.getPage(page).getPageSize()).moveRight(10).moveDown(10);
            Canvas canvas = new Canvas(documentConvertedTemp.getPage(page), rect);
            Paragraph paragraph = new Paragraph("Suggestions:\n");
            paragraph.setFontSize(25);
            // сюда нужно вставить логику добавления нужных ссылок

            //перебираем карту советов со страницами

            for (Map.Entry<Suggest, Integer> entry : mapSuggests.entrySet()) {
                //если страница совпала с той что содержится в карте совет страница, то вставляем в параграф совет!
                if (entry.getValue() == page) {
                    //paragraph.add(entry.getKey().getKeyWord());
                    PdfLinkAnnotation annotation = new PdfLinkAnnotation(rect);
                    PdfAction action = PdfAction.createURI(entry.getKey().getUrl());
                    annotation.setAction(action);
                    Link link = new Link(entry.getKey().getTitle(), annotation);
                    paragraph.add(link.setUnderline());
                    paragraph.add("\n");
                    //System.out.println("Вставка совета " + entry.getKey() + " на страницу " + page + " осуществлена");
                }
            }
            canvas.add(paragraph);
            //System.out.println("Вставка совета(ов) " + page + " на страницу " + page + " осуществлена");
            //}


        }

    }

    //копируем подготовленные страницы из временного документа в финальный на свои места
    // вставка осуществляется в обратном порядке от большей страницы к меньшей, так как если делать с начала, станицы сдвигаются на количество вставок
    // и отслеживать эти сдвиги не самое большое удовольствие
    public static void insertPreparedPages(PdfDocument reader, PdfDocument writer, Map<Integer, Integer> map) {
        //System.out.println("Копируем страницы c конца временного файла если их нужно вставить");
        //для того чтобы скопировать страницы из временного документа нам нужно открыть его на чтение
        //var readerPdfTemp = new PdfReader(directoryConvertedPdfs + "\\temp" + fileIn.getName());
        //var documentConvertedTempReading = new PdfDocument(readerPdfTemp); //документ для редактирования временный
        //формируем список с обратным порядком страниц для вставки в финальный документ с конца
        List<Integer> pagesInDescendingOrder = new ArrayList<>(map.keySet());
        Collections.sort(pagesInDescendingOrder, Collections.reverseOrder());

        //последний шаг нужно скопировать страницы из временного документа в финальный
        for (int i = 0; i < pagesInDescendingOrder.size(); i++) {
            //System.out.println("Обработка страницы " + pagesInDescendingOrder.get(i) + " вставляем в финальный документ страницу на позицию " + pagesInDescendingOrder.get(i)+1);
            //System.out.println("Вставили пустую страницу чтобы в нее можно было ");
            reader.copyPagesTo(pagesInDescendingOrder.get(i), pagesInDescendingOrder.get(i), writer, pagesInDescendingOrder.get(i) + 1);
        }
        reader.close();
        writer.close();
    }

}