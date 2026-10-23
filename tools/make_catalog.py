# Ortak ders kataloğunu (app/src/main/assets/catalog.json) üretir.
# Haftalık güncelleme: Drive "Ingilizce calismalar" klasörünü listele, yeni dosyaları raw listesine ve uygun konuya ekle, çalıştır.
# Kullanım: python3 tools/make_catalog.py
import json
F = {}  # title -> (id, kind)
raw = """1NDnzxRuyb9rNNWfMUkX1ls4UugEHRm3t|PPT (Reading Strategies III- Pronoun Reference)|pptx
1VuSRCy-Cpk-j1AckI4oELUsp2U5U5c1i|PPT (Vocabulary - So What Do You Study)|pptx
1IOzRZxl7kWKB8k0I8GRaLhFUORmU7s8K|Reading practice|pdf
1s8UchG_rfp8vsoVz8Vvzbftx7AaA_zJc|Opinion essay peer evaluation checklist|docx
1kMuFR99gekyniwUVy6Z3qG_yLdxSGJXX|Relative clauses context|pdf
1LZt5GhZ8G1Z4D0S0rcpGS0XdwOM-rtrX|Present Perfect Simple vs Present Perfect Progressive|docx
13yxe2AvpNjT1QpD3C6Px0RzS8cNc2zUZ|TELL ME MORE (Perfect speaking)|pdf
1nW5e-cO0DiRfwcCSlR4L-jKH7GrfC77v|Past simple vs present perfect|pdf
1QbEzUBlpn2wfIPVu3hG7a0wJFkUeGiMS|Simple vs progressive chart|docx
1SaLEtFVfkI1BkDzEZlDaJrnqxf9BveCg|Gerund / infinitive verbs list|pdf
19jAxggsC5gJmPSd6rTHxG_n5z6SZCVdo|Past perfect vs past simple exercises|pdf
1aXGA276luSearT_kQ3N7y6Yqvl2FPjlr|Narrative tenses|pdf
1A8I5xcHPunn6rv3E_LLLNfKa2j_BXiaM|Knowing a word|pptx
1uaaQqmrPAHVg7dctN2lvIz3eQF_l07_h|Why does Japan work so hard (video)|video
178GE6sEqTvkRt4OPBGrjCjWSu2m3uFG_|Stative verbs|docx
18GnLbVfnqglCJOa5RN0-tGD_7POwxXL4|PPT (Necessity, Obligation, Permission)|pptx
1DvFvEcJ7wuxyR_mAavKdBmu6BladwkyK|HANDOUT (Reading) 2|pdf
1QLiMlgvNJU5mKqZ6BNQWGaBixiTkNuUZ|WRITING HANDOUT (Sample Opinion Essays)|pdf
1Asum7vpcwyhmg3uXSGqlEHn3XmXzAnc0|ACTIVITY HANDOUT (Necessity, Obligation, Permission)|pdf
1UlfvBdR1uffoTKis29FO3fwj1kkVXX8S|INTEGRATED HANDOUT (Work Life)|pdf
1Rt9SayzcAfkSa25gJV-PKEyEd9e47sSt|HANDOUT (Vocabulary)|pdf
1mVghXEmvIUepkosxNDXvdvDnKqHN9Bec|WRITING HANDOUT (Opinion Essay) Practice I|pdf
15sIiDUTPpTbeL_OC_ULszgqV_d_lESD2|PPT (Conditional Structures)|pptx
1UKgjhsxZa-mwGIkBtUUhR8A1BBQX-ifr|HANDOUT 2 (Rewrite Conditional Structures)|pdf
1fpeQckh3OgMXMcPHeo7X5KqjKNxrBVX3|HANDOUT 1 (Conditional Structures)|pdf
12OGxdDpVOxCltlWX4WIn0JoAcIcaHFHH|HANDOUT (Vocabulary B1+ U3A)|pdf
18qqY69R8-_MzMe55C0hE-6NUZpKNEcJu|ACTIVITY HANDOUT (Present Perfect Continuous board game)|pdf
1RqEh14Pf4V2MxzMk9VMFyLYEFtwYcH5K|HANDOUT (Vocabulary and How to)|pdf
1cfNqFDQf88hJ9GtF6YQHYtvtjRZAT7H1|HANDOUT (Verb Patterns)|pdf
19FARQZFfOGOUxlLbsgGhyo2SlXiRlVbv|HANDOUT 2 (Narrative Tenses - Rewrite)|pdf
1z0hnttun7uK0lxngfKoU6FJtyztzY-jl|HANDOUT (Reading)|pdf
1vw-gAUFCWDxYUW-ARF9HvrnE6Ctzi99h|HANDOUT 1 (Narrative Tenses)|pdf
1G-u3OSbBg-oCOabj8IrSigtIDuEIgU4-|HANDOUT (Relative Clauses - Rewrite)|pdf
1qOJI2tjA68RE-9fe9c4uxw8MwmAqF-79|HANDOUT (Vocabulary 2A B)|pdf
1XQJTKOzd7yHErfJDtZuXIhw_g87EF6en|HANDOUT (Vocabulary B1+ U1A)|pdf
1FfLvcPj1FU2s9H4ogZYauewNoGmg5sLU|HANDOUT (Vocabulary B1+ U2D)|pdf
1SYSkf2Y_iTIBXhVGGs-eeVztSf0EOsFL|HANDOUT (Vocabulary How to Activity B1+ U2C)|pdf
1FRV0clrmOnAcPE9rtMbFHSAjbC1qtKPt|ACTIVITY HANDOUT 1 (Verb Patterns)|pdf
1TZ8I2SZQitw-eWcftwQVnMpJShibRybs|HANDOUT (Present Perfect, Present Perfect Continuous, Past Simple)|pdf
1cuI_rOkoz63rETncgvVguChR0KqCg7NU|ACTIVITY HANDOUT 2 (Verb Patterns)|pdf
1ZLZSx09BVUPuWmy-LI_jb93Ym2YKrC01|ACTIVITY HANDOUT (Relative Clauses board game)|pdf
1OtEPyJaPXnaR9w_wvpXgg8WKDhZWeyL8|ACTIVITY HANDOUT (Video)|pdf
1ct4nmT5QlK-7T4DWClU82BNs5-KKj1lG|ACTIVITY HANDOUT (The University of the Future)|pdf
1fTyQw7bA_uS3d-OdN6qpvEnPxgbLiqk0|L4 Quiz 1 özet kağıdı|pdf
1pGj32tqSGoQ4TIq8DDcCBrgJ3MoLJbc4|PPT SPEAKING (Talking about Yourself)|pptx
17s_izwe8xz880uEz-C8agQ45pj2CJUGY|PPT SPEAKING (The Mic is Yours)|pptx
1gP6ozCIo1QBWGNl9rcgbdnTIra9anRRT|PPT (Using AI to Learn English)|pptx
1BJkYGnD5kDW8cdNZTm8AxILKPcsYread|PPT SPEAKING (Learning)|pptx
1a6rxpbgYHlWfIyP8hOPNEfGz0cHZStqf|PPT (Group Project: Our Class Our Story)|pptx
1GxsUITQQbwG84gLK3bYAdIN9cvzDEKNX|PPT (Haftalık Ders Programı ve İçeriği)|pptx
11eGwGk0WCJa87pTNAv-TIR3DxRsloxku|WRITING HANDOUT (Points to Notice)|pdf
1stTHvWIB4TpL3kxI1nI0CpCNpIg9EOuG|WRITING HANDOUT (Concluding Paragraph)|pdf
1zL-Tyf42X-RPoUzlead8Sz_FqNyoqw4D|WRITING HANDOUT (Body Paragraphs)|pdf
1nUH6_w6YDrYSraQ43d84D8ZOYJT_Hy7O|WRITING HANDOUT (Introductory Paragraph)|pdf
1_9JhHQsR0xzMDlZ5A4Y4SgO0Z4VerCfZ|INTEGRATED HANDOUT (Big Changes in History)|pdf
1IHA02eMFtq3GZqW48k2ouvorxF8qNIeB|PPT SPEAKING (Pet Hates)|pptx
1sj-tkS1uZ6oVvTSYo5m5L7_GIyIPWYcY|PPT (Reading Strategies II - Topic, Main Ideas, Supporting Details)|pptx
1SBDT-c46y4686M3W35oKnAFquHG8qXX4|PPT (Reading Strategies I - Introduction)|pptx
1N2MLZSfpH2x5ZAjMhBnQdBukmRqeEl3d|PPT (V-ing / to infinitive with Relative Clauses)|pptx
1TXOhEumYaWnhRKCxRUpLJDW90DShD0rD|PPT (Relative Clauses)|pptx
15AphUXD7sQD2ObTBvmT5oUwykRFNeAAI|WRITING HANDOUT (Opinion Essay Linkers)|pdf
1x6WomYu8ZQEZnMB2z93cv3WHAyBW129m|PPT SPEAKING (Habits)|pptx
15yJsxyWNouJK1YDmcB7YFPVYO_rovyzr|INTEGRATED HANDOUT (Habits)|pdf
13z-hdjarPJrzeFbP0_qGgOE8hLbl3uUe|Video (Activity)|video
18c9r8CQhZS4ZaVvNHQRzm6eVgEW8Fsug|INTEGRATED HANDOUT (The New Normal)|pdf
13LGwFhGdaBz6gQkLteBwpdHPvURQ7_JX|PPT (Present Perfect Continuous)|pptx
1Gg_kW-o8pFEe93vi79b__oyP7phwgwYZ|Video 2 - Hoarder Denise's House Tour|video
1cYE7L6dB7A74dHvXjFfHXGwGNL3BHhQb|Video 1 - The Hoarding Habit|video
1I0SQEbWuhXFD8Sdp1mzfyop_7Z6xzbcO|PPT (except for, apart from, not even)|pptx
1l8Yow9IGSUcqS_D7xQa98GknSxtWLkaj|INTEGRATED HANDOUT (The Hoarding Habit)|pdf
1g1ls0o6_FOSA_Vfn3ABtJ9ZWuxKLQXWd|PPT (Verb Patterns)|pptx
18hQkuiQ9V2Ha05t4dSWOLFAPJTj6_VkK|PPT (From Opinion Paragraph to Opinion Essay)|pptx
1lVmyOZtMdQ_0tyQpEUjZNQq7lOzt6qKi|WRITING HANDOUT (From Opinion Paragraph to Opinion Essay)|pdf
1p8rd01O3PjbeXqeWiYvTlnz6hkL3yyiR|Activity handout video|video
12XEaPoG-DC7c3ToyWE9_7hC733RIp1QD|REVISION HANDOUT (B1)|pdf
1zYt2XUdXQ2-U61wwm3-FyeWMKXp5tYVe|INTEGRATED HANDOUT (The Algorithm Knows You)|pdf
1hTGR11zxLHgN1i9BmS5LacfgrXeRxeHk|PPT WRITING (Sample Opinion Essays)|pptx
1yYZ9df3kBHk1FEdp9S5prjgRjur9sh2I|PPT (From University to the Office)|pptx
1l-jweFW3QchWIkE9jIqrzGW1ErpEpCC2|PPT SPEAKING (Work Life)|pptx
1ofKtAlr9IWtDtIMln3O7PksihHQmgKXL|PPT (Listening Strategies I - Introduction)|pptx
1v1QyueMgQW6cbB3j4jMu18mSmbfpott8|L4 Quiz 1 Grammar çalışma kağıdı|docx
1pX5oJG6QAipxCETROpZ_-PM95qoVIBjg|L4 Quiz 1 deneme testi|html"""
for line in raw.splitlines():
    i,t,k = line.split("|"); F[t]=(i,k)
used=set()
def R(*titles):
    out=[]
    for t in titles:
        i,k=F[t]; used.add(t)
        out.append({"title":t,"url":f"https://drive.google.com/file/d/{i}/view","kind":k})
    return out

courses = [
 {"id":"grammar","name":"Grammar","emoji":"🧩","color":"#2F5BD3","topics":[
   {"id":"narrative","name":"Narrative Tenses","resources":R("HANDOUT 1 (Narrative Tenses)","HANDOUT 2 (Narrative Tenses - Rewrite)","Narrative tenses","Past perfect vs past simple exercises")},
   {"id":"perfect","name":"Present Perfect & Continuous / Past Simple","resources":R("PPT (Present Perfect Continuous)","HANDOUT (Present Perfect, Present Perfect Continuous, Past Simple)","ACTIVITY HANDOUT (Present Perfect Continuous board game)","Past simple vs present perfect","Present Perfect Simple vs Present Perfect Progressive","Simple vs progressive chart","Stative verbs")},
   {"id":"verbpatterns","name":"Verb Patterns","resources":R("PPT (Verb Patterns)","HANDOUT (Verb Patterns)","ACTIVITY HANDOUT 1 (Verb Patterns)","ACTIVITY HANDOUT 2 (Verb Patterns)","Gerund / infinitive verbs list")},
   {"id":"relative","name":"Relative Clauses","resources":R("PPT (Relative Clauses)","PPT (V-ing / to infinitive with Relative Clauses)","HANDOUT (Relative Clauses - Rewrite)","ACTIVITY HANDOUT (Relative Clauses board game)","Relative clauses context")},
   {"id":"conditionals","name":"Conditional Structures","resources":R("PPT (Conditional Structures)","HANDOUT 1 (Conditional Structures)","HANDOUT 2 (Rewrite Conditional Structures)")},
   {"id":"modals","name":"Necessity, Obligation, Permission","resources":R("PPT (Necessity, Obligation, Permission)","ACTIVITY HANDOUT (Necessity, Obligation, Permission)")},
   {"id":"except","name":"except for / apart from / not even","resources":R("PPT (except for, apart from, not even)")},
 ]},
 {"id":"vocabulary","name":"Vocabulary","emoji":"🔤","color":"#8E63CE","topics":[
   {"id":"vocab-u1","name":"Unit 1 (1A)","resources":R("HANDOUT (Vocabulary B1+ U1A)")},
   {"id":"vocab-u2","name":"Unit 2 (2A–2D)","resources":R("HANDOUT (Vocabulary 2A B)","HANDOUT (Vocabulary How to Activity B1+ U2C)","HANDOUT (Vocabulary B1+ U2D)","HANDOUT (Vocabulary and How to)")},
   {"id":"vocab-u3","name":"Unit 3 (3A)","resources":R("HANDOUT (Vocabulary B1+ U3A)")},
   {"id":"vocab-general","name":"Kelime öğrenme","resources":R("HANDOUT (Vocabulary)","PPT (Vocabulary - So What Do You Study)","Knowing a word")},
 ]},
 {"id":"reading","name":"Reading","emoji":"📖","color":"#3FA36B","topics":[
   {"id":"reading-strategies","name":"Reading Strategies (I–III)","resources":R("PPT (Reading Strategies I - Introduction)","PPT (Reading Strategies II - Topic, Main Ideas, Supporting Details)","PPT (Reading Strategies III- Pronoun Reference)")},
   {"id":"reading-practice","name":"Reading alıştırmaları","resources":R("HANDOUT (Reading)","HANDOUT (Reading) 2","Reading practice")},
 ]},
 {"id":"listening","name":"Listening","emoji":"🎧","color":"#3AA6B9","topics":[
   {"id":"listening-strategies","name":"Listening Strategies","resources":R("PPT (Listening Strategies I - Introduction)")},
 ]},
 {"id":"writing","name":"Writing","emoji":"✍️","color":"#E0893A","topics":[
   {"id":"essay-structure","name":"Opinion Essay: giriş, gelişme, sonuç","resources":R("WRITING HANDOUT (Introductory Paragraph)","WRITING HANDOUT (Body Paragraphs)","WRITING HANDOUT (Concluding Paragraph)","WRITING HANDOUT (Points to Notice)")},
   {"id":"paragraph-to-essay","name":"Opinion Paragraph → Opinion Essay","resources":R("PPT (From Opinion Paragraph to Opinion Essay)","WRITING HANDOUT (From Opinion Paragraph to Opinion Essay)")},
   {"id":"essay-samples","name":"Örnek essay'ler ve pratik","resources":R("WRITING HANDOUT (Sample Opinion Essays)","PPT WRITING (Sample Opinion Essays)","WRITING HANDOUT (Opinion Essay) Practice I","Opinion essay peer evaluation checklist")},
   {"id":"linkers","name":"Linkers (bağlaçlar)","resources":R("WRITING HANDOUT (Opinion Essay Linkers)")},
 ]},
 {"id":"speaking","name":"Speaking","emoji":"🗣️","color":"#D65B7F","topics":[
   {"id":"speaking-topics","name":"Speaking konuları","resources":R("PPT SPEAKING (Talking about Yourself)","PPT SPEAKING (Habits)","PPT SPEAKING (Pet Hates)","PPT SPEAKING (Learning)","PPT SPEAKING (Work Life)","PPT SPEAKING (The Mic is Yours)")},
   {"id":"speaking-tips","name":"Konuşmayı geliştirme","resources":R("TELL ME MORE (Perfect speaking)","PPT (Using AI to Learn English)")},
   {"id":"presentation","name":"Sunum ve grup projesi","resources":R("PPT (Group Project: Our Class Our Story)")},
 ]},
 {"id":"units","name":"Ünite materyalleri","emoji":"🧭","color":"#5B7FD6","topics":[
   {"id":"unit-habits","name":"Habits / The Hoarding Habit","resources":R("INTEGRATED HANDOUT (Habits)","INTEGRATED HANDOUT (The Hoarding Habit)","Video 1 - The Hoarding Habit","Video 2 - Hoarder Denise's House Tour")},
   {"id":"unit-work","name":"Work Life","resources":R("INTEGRATED HANDOUT (Work Life)","PPT (From University to the Office)","Why does Japan work so hard (video)")},
   {"id":"unit-change","name":"Big Changes / The New Normal","resources":R("INTEGRATED HANDOUT (Big Changes in History)","INTEGRATED HANDOUT (The New Normal)")},
   {"id":"unit-future","name":"University of the Future / Algorithm","resources":R("ACTIVITY HANDOUT (The University of the Future)","INTEGRATED HANDOUT (The Algorithm Knows You)")},
   {"id":"unit-video","name":"Video etkinlikleri","resources":R("ACTIVITY HANDOUT (Video)","Video (Activity)","Activity handout video")},
 ]},
 {"id":"exam-prep","name":"Sınav hazırlık","emoji":"🎯","color":"#C62828","topics":[
   {"id":"quiz1-l4","name":"L4 Quiz 1 hazırlık","resources":R("L4 Quiz 1 özet kağıdı","L4 Quiz 1 Grammar çalışma kağıdı","L4 Quiz 1 deneme testi")},
   {"id":"revision","name":"Genel tekrar (B1)","resources":R("REVISION HANDOUT (B1)")},
   {"id":"program","name":"Haftalık program ve ders saatleri","resources":R("PPT (Haftalık Ders Programı ve İçeriği)")},
 ]},
]
missing=set(F)-used
assert not missing, missing

ALL=["L1","L2","L3","L4"]
def E(id,title,date,time,levels,kind,slot="",place="",notes="",topics=()):
    return {"id":id,"course":"İngilizce","title":title,"date":date,"time":time,"slot":slot,"levels":levels,"kind":kind,"place":place,"notes":notes,"topics":list(topics)}
exams=[
 E("we1-l1","Writing Exam 1 · Daily Routine","2026-10-12","13:40",["L1"],"exam","5. ders"),
 E("quiz1","Quiz 1","2026-10-16","11:45",ALL,"exam","4. ders",notes="L4 konuları: narrative tenses, present perfect, verb patterns, relative clauses, Unit 1–2 kelimeler, reading",topics=["narrative","perfect","verbpatterns","relative","except","vocab-u1","vocab-u2","reading-strategies","quiz1-l4"]),
 E("st1-topic-l34","Speaking Task 1 konu ataması","2026-10-19","09:00",["L3","L4"],"task",topics=["speaking-topics"]),
 E("we1-l2","Writing Exam 1 · Narrative Paragraph","2026-10-23","11:45",["L2"],"exam","4. ders",topics=["narrative"]),
 E("st1-topic-l12","Speaking Task 1 konu ataması","2026-11-02","09:00",["L1","L2"],"task",topics=["speaking-topics"]),
 E("we1-l3","Writing Exam 1 · Opinion Paragraph","2026-11-06","11:45",["L3"],"exam","4. ders",topics=["paragraph-to-essay","linkers"]),
 E("we1-l4","Writing Exam 1 · Opinion Essay","2026-11-06","11:45",["L4"],"exam","4. ders",topics=["essay-structure","paragraph-to-essay","essay-samples","linkers"]),
 E("mp1-topic","Mini Presentation konu ataması · Grup 1","2026-11-09","09:00",["L3"],"task",topics=["presentation"]),
 E("p1-topic","Presentation konu ataması · Grup 1","2026-11-09","09:00",["L4"],"task",topics=["presentation"]),
 E("pt1","Progress Test 1","2026-11-13","09:00",ALL,"exam",notes="Saat duyurulacak"),
 E("mp2-topic","Mini Presentation konu ataması · Grup 2","2026-11-16","09:00",["L3"],"task",topics=["presentation"]),
 E("p2-topic","Presentation konu ataması · Grup 2","2026-11-16","09:00",["L4"],"task",topics=["presentation"]),
 E("st1-sub-l34","Speaking Task 1 teslim","2026-11-16","17:00",["L3","L4"],"task","17:00'ye kadar",topics=["speaking-topics","speaking-tips"]),
 E("mp3-topic","Mini Presentation konu ataması · Grup 3","2026-11-23","09:00",["L3"],"task",topics=["presentation"]),
 E("p3-topic","Presentation konu ataması · Grup 3","2026-11-23","09:00",["L4"],"task",topics=["presentation"]),
 E("mp1","Mini Presentation · Grup 1","2026-11-23","09:00",["L3"],"presentation","10. hafta (23–27 Kasım)",topics=["presentation"]),
 E("p1","Presentation · Grup 1","2026-11-23","09:00",["L4"],"presentation","10. hafta (23–27 Kasım)",topics=["presentation"]),
 E("quiz2","Quiz 2","2026-11-27","11:45",ALL,"exam","4. ders"),
 E("st1-sub-l12","Speaking Task 1 teslim","2026-11-30","17:00",["L1","L2"],"task","17:00'ye kadar",topics=["speaking-topics","speaking-tips"]),
 E("st2-topic-l12","Speaking Task 2 (Role-play) konu ataması","2026-11-30","09:00",["L1","L2"],"task",topics=["speaking-topics"]),
 E("mp2","Mini Presentation · Grup 2","2026-11-30","09:00",["L3"],"presentation","11. hafta (30 Kasım–4 Aralık)",topics=["presentation"]),
 E("p2","Presentation · Grup 2","2026-11-30","09:00",["L4"],"presentation","11. hafta (30 Kasım–4 Aralık)",topics=["presentation"]),
 E("st2","Speaking Task 2 · Role-play (sınıfta)","2026-12-07","09:00",["L1","L2"],"exam","12. hafta (7–11 Aralık)",topics=["speaking-topics"]),
 E("mp3","Mini Presentation · Grup 3","2026-12-07","09:00",["L3"],"presentation","12. hafta (7–11 Aralık)",topics=["presentation"]),
 E("p3","Presentation · Grup 3","2026-12-07","09:00",["L4"],"presentation","12. hafta (7–11 Aralık)",topics=["presentation"]),
 E("pt2","Progress Test 2","2026-12-18","09:00",ALL,"exam",notes="Saat duyurulacak"),
 E("we2-l1","Writing Exam 2 · Narrative Paragraph","2026-12-21","13:40",["L1"],"exam","5. ders",topics=["narrative"]),
 E("we2-l2","Writing Exam 2 · Opinion Paragraph","2026-12-21","13:40",["L2"],"exam","5. ders",topics=["paragraph-to-essay","linkers"]),
 E("we2-l3","Writing Exam 2 · Opinion Paragraph","2026-12-21","13:40",["L3"],"exam","5. ders",topics=["paragraph-to-essay","linkers"]),
 E("we2-l4","Writing Exam 2 · Opinion Essay","2026-12-21","13:40",["L4"],"exam","5. ders",topics=["essay-structure","paragraph-to-essay","essay-samples","linkers"]),
 E("makeup","Quiz ve Progress Test telafi","2026-12-28","09:00",ALL,"exam"),
 E("gateway","Gateway Exam","2027-01-04","09:00",ALL,"exam","4–6 Ocak",topics=["revision"]),
 E("proficiency","Proficiency Exam","2027-01-11","09:00",ALL,"exam","11–12 Ocak",topics=["revision"]),
]
# Sadece L4: diğer kurların sınavları çıkarılır, kur bilgisi tutulmaz.
exams=[{k:v for k,v in e.items() if k!="levels"} for e in exams if "L4" in e["levels"]]
cat={"updated":"2026-10-23b","termStart":"2026-09-21","note":"SouNote ortak ders kataloğu (Ankara Üniversitesi YDYO 2026-2027 Güz, L4). Uygulama bu dosyayı Drive'daki SouNote-katalog.json dosyasından okur; değişiklik herkesin uygulamasında görünür.",
     "courses":courses,"exams":exams}
json.dump(cat, open('app/src/main/assets/catalog.json','w'), ensure_ascii=False, indent=1)
print(len(F), "dosya,", sum(len(t['resources']) for c in courses for t in c['topics']), "kaynak,", len(exams), "sınav/ödev")
