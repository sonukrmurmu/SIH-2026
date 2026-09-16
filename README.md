# SIH-2026
wrong code working every code is here failed code proper code will be added to proper work folder that r in final version
hey guys dont try to read it just feel it
i have made it straight to the point and pretty understable i think
atleast it is understable for me
the main files i worked with are in 
/translator/app/main/src/
here the assets contain the dictionary database of 1000 words and ai model inside indic trans folder
app/src/main/cpp/ this contain the c++ place native cpp that text from the android ui using passed into the ai model
app/src/main/java/.../
it has two files
one is mainactivity.kt it reads input from ui and pass it into the c engine and recive output from c++ engine and pass it to
the mainactivity which pass it to the output screen
and another is dictionary db kit it analyse the dictionary where model can sometime fail in one word or two words output
app/src/main/res/ this contain the santali font which is inside the subdirectory
androidmanifest xml contain the permission we need which is nothing mostly but in future we are gonna work with it 
and in loction there is build.gradle.kts location is
it contain the instruction to build the apk using 64 bit libraries avoiding 32 bit because it was producing error
and second is it to not zip our ai model 
and third is forcing the compiler to be form 2017 revised standard because ai libraries mainly work in this 
and there is cmakelist.txt the location is app/src/main/cpp/
it main works as a proper linker it links the c++ ui main activity etc properly and
one last thing i downloaded the model from her 
https://huggingface.co/adalat-ai/ct2-rotary-indictrans2-en-indic-dist-200M/tree/main/en-indic-200m-ct2/ctranslate2_model
and u will need permission to access this 
so create hugging face account and just accept the terms and condition u r eady to go the model size is 847 mb so downloaded that one and everything 
is inside the git so dont worry

---------------------------------------------------------------------------------------------------------------------------------------------------------
her is the location of ui file which is going too be handled by anamika so this section is to her 
u can use your exampt prompt whatever the design and simply use chagtpt gemini claude whatever feeds photos of the ui and it will output text code
for the xml file that we simply put the code and our ui is ready and i will link it and btw try to learn some ui desing in android studio
the location of xml file which u will work with is app/src/main/res/layout/activity_main.xml 
xml file is the ui file
and after our core functionality is ended and working we will work in adding new feature to impress thejudges ofcourse .

