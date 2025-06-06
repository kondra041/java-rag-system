# About

This repository presents an implementation of RAG LLM system for API tests generation.

The project is completed during the preparation of Dmitriy S. Kondrukov's work under "Testing of software" at SPbPU Institute of Computer Science and Cybersecurity (SPbPU ICSC).

## Authors and contributors

- **Advisor and contributor**: Vladimir A. Parkhomenko, Senior Lecturer of SPbPU ICSC
- **Main contributor**: Dmitriy S. Kondrukov, student of SPbPU ICSC

## License

### Warranty
The contributors give no warranty for the using of the software.

### License terms
This program is open to use anywhere and is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html).


## Инструкция по установке

1. **База данных Milvus**  
   Разверните Milvus локально или в облаке (например, Zilliz Cloud):

2. **Установка Ollama**
    Скачайте и установите Ollama с официального сайта. Можно воспользоваться имеющимся в проекте Docker контейнером: 
```bash
   docker compose up  # Для локального развертывания ollama
```

3. **Установка моделей**
    Установите необходимые модели через Ollama:
```bash
    ollama pull nomic-embed-text  # Модель для эмбеддингов
    ollama pull codestral            # Или другую LLM-модель на выбор
```

4. **Настройка конфигурации**
    Заполните параметры в файле application.properties

5. **Сборка и запуск**
```bash
    mvn clean install     # Сборка проекта
    mvn exec:java         # Запуск приложения
```