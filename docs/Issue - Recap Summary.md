Recap Summary :
FEATURE WORKORDER:

* penambahan attribute "donation value" pada status WO Astri pada Smallworld. -> nunggu update API doc atau info dari mereka? 
* attribute donation di pisahkan berdasarkan status inhouse dan by vendor. -> nunggu update API doc atau info dari mereka? 
* jika value donation "by vendor" maka akan tergenerate kedalam boq excel & juga BoQ Json (value change after pph). -> ini perlu BOQ code apa?
* jika status "inhouse" maka tidak tergenerate dalam boq apd excel.
* penarikan data KMZ pada Astri wajib realtime. kemungkinan perbaikan sistem atau SOP di my rep?? to be confirmed ke my rep.
* penambahan feature upload kmz & boq Excel pada menu tools bar wo Astri smallworld. --> ini maksudnya upload ke astri?? masih nunggu API Doc. ini perlu dipastikan proses atau action uploadnya manual atau otomatis by sistem?

* hasil generate kabel pada BoQ dan BoQ as JSON masih belum synchronize. *perlu kalibarasi ulang
Issue BOQ: Excel vs API --> menunggu update excel terbaru
1. Cable masih tidak match (UG dan AE)-> kemungkinan karena sumber perhitungan salah 
2. Asesoris belum masuk (terutama ketika ada POLE khusus AERIAL)

note :
SAP Code Permit on BoQ Exel 500002155. 
in cell "D204 = 1" & "E204 = value donation"

* pada saat processing "Migrate to Design" untuk mempercepat proses generate counting. 
* Mandatory pada attribute: 
* Aerial : kabel, slingwire, pole, fat, fdt, 
* Undergorund : kabel, slingwire, pole, fat, fdt, hh akses, open trenching, boring, subduct, talipancing, pedestal.

Propose solution 
-> add checkbox if true than execute:
* untuk process counting Homepass, Boundary FAT & Boundary Cluster menjadi process optional.
-> handle update homepass in fdt object using amount of hp in placemarks, karena dipakai buat feature ManCore
-> untuk pole atau uub selain fat dan fdt itu tidak perlu di geser.
-> untuk t-wey tidak perlu di geser

* Issue design cluster BKS003271 
-> ada isu tiang 7-4 tidak ke migrasi?
-> fdt 8 yang ke migrasi 6
-> ring name -> multiple

* process validasi counting homepass di buat feature terpisah dari process nya migrate to design.
-> ToBeConfirmed

* jika Design FDT lebih dari 2 unit, BoQ masih belum bisa tergenerate. (ini issue excel)
-> kemungkinan karena isu ring name yg harusnya nama fdt jadi "multiple"


* Pada saat “Migrate Design” data KMZ tidak tertarik/tidak terbaca untuk cluster dengan status “Successful”
Cluster Demo : PLB005917
-> perlu di revisit


FEATURE MANCORE:
* booking core pada menu WO Astri berhasil di lakukan dan perlu peninjauan untuk ascending core use.
-> ini pengennya bisa di sort ASC : ini tinggal kasih tahu lagi how to nya.
* feature tambahan untuk pe-labelan FAT & Poles secara automate dengan Smallworld. *upcoming
-> next Change Request

* Pada saat cek CoreMan cluster, data Subfeeder dan Main Feeder hanya terbaca salah satunya
-> how to: klik kable cluter, pas di get mancore data di table subfeeder connectivity kosong, perlu di revisit
Cluster Demo : PLB004827
SubFeeder Demo : -
Main Feeder : FPLB0049

* Pada saat testing CoreMan tidak bisa mengembalikan status core pada Feeder seperti semula 
Cluster Demo : PLB004827
Main Feeder : FPLB0049
-> ini perlu di reproduce semua use case. start idle back to idle, start active back to active or start booked back to booked
-> action perlu di kasih tahu how to nya

Test 1 : 
T : Cluster tersebut telah berstaus “Active” dan saya test untuk melakukan “Release"
R: Pada data ManCore SmallWorld pada Tube dan Core yang telah active sebelumnya berhasil berubah statusnya menjadi “Idle” begitu juga pada ASTRI menjadi “Release”
Test 2 :
T : Sebaliknya
R : Ketika ingin melakukan blocking Tube & Core yang diinginkan untuk proses “Booked” atau “Active" icon panah “Kiri” maupun “Kanan” pada SmallWorld tidak dapat di klik


FEATURE NISA
* 