package com.example.digital_subsidy.config;

import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
public class SeedDataConfig {

    @Bean
    public CommandLineRunner initData(RoleRepository roleRepository,
                                       UserRepository userRepository,
                                       BeneficiaryRepository beneficiaryRepository,
                                       OfficerRepository officerRepository,
                                       RegionRepository regionRepository,
                                       SchemeRepository schemeRepository,
                                       EligibilityCriteriaRepository criteriaRepository,
                                       RequiredDocumentRepository reqDocRepo,
                                       com.example.digital_subsidy.repository.DisbursementMilestoneRepository milestoneRepo,
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Roles
            Role roleAdmin = createRoleIfNotFound(roleRepository, "ROLE_ADMIN");
            Role roleField = createRoleIfNotFound(roleRepository, "ROLE_FIELD_OFFICER");
            Role roleDistrict = createRoleIfNotFound(roleRepository, "ROLE_DISTRICT_OFFICER");
            Role roleFinance = createRoleIfNotFound(roleRepository, "ROLE_FINANCE_APPROVER");
            Role roleBeneficiary = createRoleIfNotFound(roleRepository, "ROLE_BENEFICIARY");

            // 2. Users & Officers
            createUserIfNotFound(userRepository, passwordEncoder, "johnmekala4277@gmail.com", "9876543210", "System Administrator", "Admin@123", roleAdmin);

            // Field Officer
            User fieldUser1 = createUserIfNotFound(userRepository, passwordEncoder, "pabbuletihari4291@gmail.com", "8123456789", "Hari Pabbuleti (Field Officer)", "Field@123", roleField);
            createOfficerIfNotFound(officerRepository, fieldUser1, "FIELD_OFFICER", "Andhra Pradesh", "Kakinada");

            // District Officer
            User districtUser = createUserIfNotFound(userRepository, passwordEncoder, "kalyanramvase377@gmail.com", "7890123456", "Sita Sharma (District Collectorate)", "District@123", roleDistrict);
            createOfficerIfNotFound(officerRepository, districtUser, "DISTRICT_OFFICER", "Andhra Pradesh", "Kakinada");

            // Finance Approver
            User financeUser = createUserIfNotFound(userRepository, passwordEncoder, "pabbuletihari0@gmail.com", "9123456780", "Anil Mehta (Finance Approver)", "Finance@123", roleFinance);
            createOfficerIfNotFound(officerRepository, financeUser, "FINANCE_APPROVER", "Andhra Pradesh", "Kakinada");

            if (userRepository.findByEmail("hari.pabbu@gmail.com").isEmpty()) {
                User benUser = new User();
                benUser.setFullName("Hari Pabbuleti");
                benUser.setEmail("hari.pabbu@gmail.com");
                benUser.setMobileNumber("9988776655");
                benUser.setPassword(passwordEncoder.encode("Hari@1234"));
                benUser.getRoles().add(roleBeneficiary);

                Beneficiary ben = new Beneficiary();
                ben.setUser(benUser);
                ben.setAge(28);
                ben.setGender("Male");
                ben.setCategory("OBC");
                ben.setState("Andhra Pradesh");
                ben.setDistrict("Kakinada");
                ben.setAddress("12/4 Gandhi Nagar");
                ben.setPincode("533001");
                ben.setIdentityType("Aadhaar");
                ben.setIdentityNumber("123456789012");
                ben.setIdentityStatus("VERIFIED");
                beneficiaryRepository.save(ben);
            }

            // 3. Regions Data - All 28 Indian States
            seedRegions(regionRepository);

            // 4. Schemes, Eligibility Criteria & Required Documents Data
            seedSchemes(schemeRepository, criteriaRepository, reqDocRepo, milestoneRepo);
        };
    }

    private Role createRoleIfNotFound(RoleRepository repo, String name) {
        return repo.findByName(name).orElseGet(() -> repo.save(new Role(name)));
    }

    private User createUserIfNotFound(UserRepository repo, PasswordEncoder encoder, String email, String mobile, String name, String rawPwd, Role role) {
        Optional<User> byEmail = repo.findByEmail(email);
        if (byEmail.isPresent()) {
            return byEmail.get();
        }

        if ("ROLE_ADMIN".equals(role.getName())) {
            List<User> admins = repo.findByRolesName("ROLE_ADMIN");
            if (!admins.isEmpty()) {
                User existingAdmin = admins.get(0);
                existingAdmin.setEmail(email);
                return repo.save(existingAdmin);
            }
        }

        Optional<User> byMobile = repo.findAll().stream()
                .filter(u -> mobile != null && mobile.equals(u.getMobileNumber()))
                .findFirst();
        if (byMobile.isPresent()) {
            User existingUser = byMobile.get();
            existingUser.setEmail(email);
            return repo.save(existingUser);
        }

        User user = new User();
        user.setEmail(email);
        user.setMobileNumber(mobile);
        user.setFullName(name);
        user.setPassword(encoder.encode(rawPwd));
        user.getRoles().add(role);
        return repo.save(user);
    }



    private void createOfficerIfNotFound(OfficerRepository repo, User user, String type, String state, String district) {
        if (repo.findByUserEmail(user.getEmail()).isEmpty()) {
            Officer officer = new Officer(user, user.getFullName(), user.getEmail(), user.getMobileNumber(), type, state, district);
            repo.save(officer);
        }
    }


    private void seedRegions(RegionRepository repo) {
        // 1. Andhra Pradesh - 26 districts
        seedStateRegions(repo, "Andhra Pradesh", List.of(
            "Alluri Sitharama Raju", "Anakapalli", "Ananthapuramu", "Annamayya", "Bapatla",
            "Chittoor", "Dr. B.R. Ambedkar Konaseema", "East Godavari", "Eluru", "Guntur",
            "Kakinada", "Krishna", "Kurnool", "NTR Vijayawada", "Nandyal",
            "Palnadu", "Parvathipuram Manyam", "Prakasam", "Sri Potti Sriramulu Nellore", "Sri Sathya Sai",
            "Srikakulam", "Tirupati", "Visakhapatnam", "Vizianagaram", "West Godavari", "YSR Kadapa"
        ));
        // 2. Arunachal Pradesh - 26 districts
        seedStateRegions(repo, "Arunachal Pradesh", List.of(
            "Anjaw", "Changlang", "Dibang Valley", "East Kameng", "East Siang",
            "Itanagar Capital Complex", "Kamle", "Kra Daadi", "Kurung Kumey", "Lepa Rada",
            "Lohit", "Longding", "Lower Dibang Valley", "Lower Siang", "Lower Subansiri",
            "Namsai", "Pakke-Kessang", "Papum Pare", "Shi Yomi", "Siang",
            "Tawang", "Tirap", "Upper Siang", "Upper Subansiri", "West Kameng", "West Siang"
        ));
        // 3. Assam - 35 districts
        seedStateRegions(repo, "Assam", List.of(
            "Bajali", "Baksa", "Barpeta", "Biswanath", "Bongaigaon",
            "Cachar", "Charaideo", "Chirang", "Darrang", "Dhemaji",
            "Dhubri", "Dibrugarh", "Dima Hasao", "Goalpara", "Golaghat",
            "Hailakandi", "Hojai", "Jorhat", "Kamrup", "Kamrup Metropolitan",
            "Karbi Anglong", "Karimganj", "Kokrajhar", "Lakhimpur", "Majuli",
            "Morigaon", "Nagaon", "Nalbari", "Sivasagar", "Sonitpur",
            "South Salmara-Mankachar", "Tamulpur", "Tinsukia", "Udalguri", "West Karbi Anglong"
        ));
        // 4. Bihar - 38 districts
        seedStateRegions(repo, "Bihar", List.of(
            "Araria", "Arwal", "Aurangabad", "Banka", "Begusarai",
            "Bhagalpur", "Bhojpur", "Buxar", "Darbhanga", "East Champaran",
            "Gaya", "Gopalganj", "Jamui", "Jehanabad", "Kaimur",
            "Katihar", "Khagaria", "Kishanganj", "Lakhisarai", "Madhepura",
            "Madhubani", "Munger", "Muzaffarpur", "Nalanda", "Nawada",
            "Patna", "Purnia", "Rohtas", "Saharsa", "Samastipur",
            "Saran", "Sheikhpura", "Sheohar", "Sitamarhi", "Siwan",
            "Supaul", "Vaishali", "West Champaran"
        ));
        // 5. Chhattisgarh - 33 districts
        seedStateRegions(repo, "Chhattisgarh", List.of(
            "Balod", "Baloda Bazar", "Balrampur", "Bastar", "Bemetara",
            "Bijapur", "Bilaspur", "Dantewada", "Dhamtari", "Durg",
            "Gariaband", "Gaurela-Pendra-Marwahi", "Janjgir-Champa", "Jashpur", "Kabirdham",
            "Kanker", "Khairagarh-Chhuikhadan-Gandai", "Kondagaon", "Korba", "Koriya",
            "Mahasamund", "Manendragarh-Chirmiri-Bharatpur", "Mohla-Manpur-Ambagarh Chowki",
            "Mungeli", "Narayanpur", "Raigarh", "Raipur", "Rajnandgaon",
            "Sakti", "Sarangarh-Bilaigarh", "Sukma", "Surajpur", "Surguja"
        ));
        // 6. Goa - 2 districts
        seedStateRegions(repo, "Goa", List.of("North Goa", "South Goa"));
        // 7. Gujarat - 33 districts
        seedStateRegions(repo, "Gujarat", List.of(
            "Ahmedabad", "Amreli", "Anand", "Aravalli", "Banaskantha",
            "Bharuch", "Bhavnagar", "Botad", "Chhota Udaipur", "Dahod",
            "Dang", "Devbhoomi Dwarka", "Gandhinagar", "Gir Somnath", "Jamnagar",
            "Junagadh", "Kheda", "Kutch", "Mahisagar", "Mehsana",
            "Morbi", "Narmada", "Navsari", "Panchmahal", "Patan",
            "Porbandar", "Rajkot", "Sabarkantha", "Surat", "Surendranagar",
            "Tapi", "Vadodara", "Valsad"
        ));
        // 8. Haryana - 22 districts
        seedStateRegions(repo, "Haryana", List.of(
            "Ambala", "Bhiwani", "Charkhi Dadri", "Faridabad", "Fatehabad",
            "Gurugram", "Hisar", "Jhajjar", "Jind", "Kaithal",
            "Karnal", "Kurukshetra", "Mahendragarh", "Nuh", "Palwal",
            "Panchkula", "Panipat", "Rewari", "Rohtak", "Sirsa",
            "Sonipat", "Yamunanagar"
        ));
        // 9. Himachal Pradesh - 12 districts
        seedStateRegions(repo, "Himachal Pradesh", List.of(
            "Bilaspur", "Chamba", "Hamirpur", "Kangra", "Kinnaur",
            "Kullu", "Lahaul and Spiti", "Mandi", "Shimla", "Sirmaur",
            "Solan", "Una"
        ));
        // 10. Jharkhand - 24 districts
        seedStateRegions(repo, "Jharkhand", List.of(
            "Bokaro", "Chatra", "Deoghar", "Dhanbad", "Dumka",
            "East Singhbhum", "Garhwa", "Giridih", "Godda", "Gumla",
            "Hazaribagh", "Jamtara", "Khunti", "Koderma", "Latehar",
            "Lohardaga", "Pakur", "Palamu", "Ramgarh", "Ranchi",
            "Sahebganj", "Seraikela-Kharsawan", "Simdega", "West Singhbhum"
        ));
        // 11. Karnataka - 31 districts
        seedStateRegions(repo, "Karnataka", List.of(
            "Bagalkote", "Ballari", "Belagavi", "Bengaluru Rural", "Bengaluru Urban",
            "Bidar", "Chamarajanagara", "Chikkaballapura", "Chikkamagaluru", "Chitradurga",
            "Dakshina Kannada", "Davanagere", "Dharwad", "Gadag", "Hassan",
            "Haveri", "Kalaburagi", "Kodagu", "Kolar", "Koppal",
            "Mandya", "Mysuru", "Raichur", "Ramanagara", "Shivamogga",
            "Tumakuru", "Udupi", "Uttara Kannada", "Vijayanagara", "Vijayapura", "Yadgir"
        ));
        // 12. Kerala - 14 districts
        seedStateRegions(repo, "Kerala", List.of(
            "Alappuzha", "Ernakulam", "Idukki", "Kannur", "Kasaragod",
            "Kollam", "Kottayam", "Kozhikode", "Malappuram", "Palakkad",
            "Pathanamthitta", "Thiruvananthapuram", "Thrissur", "Wayanad"
        ));
        // 13. Madhya Pradesh - 55 districts
        seedStateRegions(repo, "Madhya Pradesh", List.of(
            "Agar Malwa", "Alirajpur", "Anuppur", "Ashoknagar", "Balaghat",
            "Barwani", "Betul", "Bhind", "Bhopal", "Burhanpur",
            "Chhatarpur", "Chhindwara", "Damoh", "Datia", "Dewas",
            "Dhar", "Dindori", "Guna", "Gwalior", "Harda",
            "Narmadapuram", "Indore", "Jabalpur", "Jhabua", "Katni",
            "Khandwa", "Khargone", "Maihar", "Mandla", "Mandsaur",
            "Mauganj", "Morena", "Narsinghpur", "Neemuch", "Niwari",
            "Pandhurna", "Panna", "Raisen", "Rajgarh", "Ratlam",
            "Rewa", "Sagar", "Satna", "Sehore", "Seoni",
            "Shahdol", "Shajapur", "Sheopur", "Shivpuri", "Sidhi",
            "Singrauli", "Tikamgarh", "Ujjain", "Umaria", "Vidisha"
        ));
        // 14. Maharashtra - 36 districts
        seedStateRegions(repo, "Maharashtra", List.of(
            "Ahmednagar", "Akola", "Amravati", "Chhatrapati Sambhajinagar", "Beed",
            "Bhandara", "Buldhana", "Chandrapur", "Dhule", "Gadchiroli",
            "Gondia", "Hingoli", "Jalgaon", "Jalna", "Kolhapur",
            "Latur", "Mumbai City", "Mumbai Suburban", "Nagpur", "Nanded",
            "Nandurbar", "Nashik", "Dharashiv", "Palghar", "Parbhani",
            "Pune", "Raigad", "Ratnagiri", "Sangli", "Satara",
            "Sindhudurg", "Solapur", "Thane", "Wardha", "Washim", "Yavatmal"
        ));
        // 15. Manipur - 16 districts
        seedStateRegions(repo, "Manipur", List.of(
            "Bishnupur", "Chandel", "Churachandpur", "Imphal East", "Imphal West",
            "Jiribam", "Kakching", "Kamjong", "Kangpokpi", "Noney",
            "Pherzawl", "Senapati", "Tamenglong", "Tengnoupal", "Thoubal", "Ukhrul"
        ));
        // 16. Meghalaya - 12 districts
        seedStateRegions(repo, "Meghalaya", List.of(
            "East Garo Hills", "East Jaintia Hills", "East Khasi Hills", "Eastern West Khasi Hills",
            "North Garo Hills", "Ri Bhoi", "South Garo Hills", "South West Garo Hills",
            "South West Khasi Hills", "West Garo Hills", "West Jaintia Hills", "West Khasi Hills"
        ));
        // 17. Mizoram - 11 districts
        seedStateRegions(repo, "Mizoram", List.of(
            "Aizawl", "Champhai", "Hnahthial", "Khawzawl", "Kolasib",
            "Lawngtlai", "Lunglei", "Mamit", "Saiha", "Saitual", "Serchhip"
        ));
        // 18. Nagaland - 16 districts
        seedStateRegions(repo, "Nagaland", List.of(
            "Chumoukedima", "Dimapur", "Kiphire", "Kohima", "Longleng",
            "Mokokchung", "Mon", "Niuland", "Noklak", "Peren",
            "Phek", "Shamator", "Tseminyu", "Tuensang", "Wokha", "Zunheboto"
        ));
        // 19. Odisha - 30 districts
        seedStateRegions(repo, "Odisha", List.of(
            "Angul", "Balangir", "Balasore", "Bargarh", "Bhadrak",
            "Boudh", "Cuttack", "Deogarh", "Dhenkanal", "Gajapati",
            "Ganjam", "Jagatsinghpur", "Jajpur", "Jharsuguda", "Kalahandi",
            "Kandhamal", "Kendrapara", "Kendujhar", "Khordha", "Koraput",
            "Malkangiri", "Mayurbhanj", "Nabarangpur", "Nayagarh", "Nuapada",
            "Puri", "Rayagada", "Sambalpur", "Subarnapur", "Sundargarh"
        ));
        // 20. Punjab - 23 districts
        seedStateRegions(repo, "Punjab", List.of(
            "Amritsar", "Barnala", "Bathinda", "Faridkot", "Fatehgarh Sahib",
            "Fazilka", "Ferozepur", "Gurdaspur", "Hoshiarpur", "Jalandhar",
            "Kapurthala", "Ludhiana", "Malerkotla", "Mansa", "Moga",
            "Mohali", "Sri Muktsar Sahib", "Pathankot", "Patiala", "Rupnagar",
            "Sangrur", "Shaheed Bhagat Singh Nagar", "Tarn Taran"
        ));
        // 21. Rajasthan - 50 districts
        seedStateRegions(repo, "Rajasthan", List.of(
            "Ajmer", "Alwar", "Anupgarh", "Balotra", "Banswara",
            "Baran", "Barmer", "Beawar", "Bharatpur", "Bhilwara",
            "Bikaner", "Bundi", "Chittorgarh", "Churu", "Dausa",
            "Deeg", "Didwana-Kuchaman", "Dholpur", "Dudu", "Dungarpur",
            "Gangapur City", "Hanumangarh", "Jaipur", "Jaipur Rural", "Jaisalmer",
            "Jalore", "Jhalawar", "Jhunjhunu", "Jodhpur", "Jodhpur Rural",
            "Karauli", "Kekri", "Kota", "Kotputli-Behror", "Nagaur",
            "Neem Ka Thana", "Pali", "Phalodi", "Pratapgarh", "Rajsamand",
            "Salumbar", "Sanchore", "Sawai Madhopur", "Shahpura", "Sikar",
            "Sirohi", "Sri Ganganagar", "Tijara", "Tonk", "Udaipur"
        ));
        // 22. Sikkim - 6 districts
        seedStateRegions(repo, "Sikkim", List.of(
            "East Sikkim", "North Sikkim", "Pakyong", "Soreng", "South Sikkim", "West Sikkim"
        ));
        // 23. Tamil Nadu - 38 districts
        seedStateRegions(repo, "Tamil Nadu", List.of(
            "Ariyalur", "Chengalpattu", "Chennai", "Coimbatore", "Cuddalore",
            "Dharmapuri", "Dindigul", "Erode", "Kallakurichi", "Kanchipuram",
            "Kanyakumari", "Karur", "Krishnagiri", "Madurai", "Mayiladuthurai",
            "Nagapattinam", "Namakkal", "Nilgiris", "Perambalur", "Pudukkottai",
            "Ramanathapuram", "Ranipet", "Salem", "Sivaganga", "Tenkasi",
            "Thanjavur", "Theni", "Thoothukudi", "Tiruchirappalli", "Tirunelveli",
            "Tirupathur", "Tiruppur", "Tiruvallur", "Tiruvannamalai", "Tiruvarur",
            "Vellore", "Viluppuram", "Virudhunagar"
        ));
        // 24. Telangana - 33 districts
        seedStateRegions(repo, "Telangana", List.of(
            "Adilabad", "Bhadradri Kothagudem", "Hanamkonda", "Hyderabad", "Jagtial",
            "Jangaon", "Jayashankar Bhupalpally", "Jogulamba Gadwal", "Kamareddy", "Karimnagar",
            "Khammam", "Kumuram Bheem Asifabad", "Mahabubabad", "Mahabubnagar", "Mancherial",
            "Medak", "Medchal-Malkajgiri", "Mulugu", "Nagarkurnool", "Nalgonda",
            "Narayanpet", "Nirmal", "Nizamabad", "Peddapalli", "Rajanna Sircilla",
            "Rangareddy", "Sangareddy", "Siddipet", "Suryapet", "Vikarabad",
            "Wanaparthy", "Warangal", "Yadadri Bhuvanagiri"
        ));
        // 25. Tripura - 8 districts
        seedStateRegions(repo, "Tripura", List.of(
            "Dhalai", "Gomati", "Khowai", "North Tripura",
            "Sepahijala", "South Tripura", "Unakoti", "West Tripura"
        ));
        // 26. Uttar Pradesh - 75 districts
        seedStateRegions(repo, "Uttar Pradesh", List.of(
            "Agra", "Aligarh", "Ambedkar Nagar", "Amethi", "Amroha",
            "Auraiya", "Ayodhya", "Azamgarh", "Baghpat", "Bahraich",
            "Ballia", "Balrampur", "Banda", "Barabanki", "Bareilly",
            "Basti", "Bhadohi", "Bijnor", "Budaun", "Bulandshahr",
            "Chandauli", "Chitrakoot", "Deoria", "Etah", "Etawah",
            "Farrukhabad", "Fatehpur", "Firozabad", "Gautam Buddha Nagar", "Ghaziabad",
            "Ghazipur", "Gonda", "Gorakhpur", "Hamirpur", "Hapur",
            "Hardoi", "Hathras", "Jalaun", "Jaunpur", "Jhansi",
            "Kannauj", "Kanpur Dehat", "Kanpur Nagar", "Kasganj", "Kaushambi",
            "Lakhimpur Kheri", "Kushinagar", "Lalitpur", "Lucknow", "Maharajganj",
            "Mahoba", "Mainpuri", "Mathura", "Mau", "Meerut",
            "Mirzapur", "Moradabad", "Muzaffarnagar", "Pilibhit", "Pratapgarh",
            "Prayagraj", "Rae Bareli", "Rampur", "Saharanpur", "Sambhal",
            "Sant Kabir Nagar", "Shahjahanpur", "Shamli", "Shravasti", "Siddharthnagar",
            "Sitapur", "Sonbhadra", "Sultanpur", "Unnao", "Varanasi"
        ));
        // 27. Uttarakhand - 13 districts
        seedStateRegions(repo, "Uttarakhand", List.of(
            "Almora", "Bageshwar", "Chamoli", "Champawat", "Dehradun",
            "Haridwar", "Nainital", "Pauri Garhwal", "Pithoragarh", "Rudraprayag",
            "Tehri Garhwal", "Udham Singh Nagar", "Uttarkashi"
        ));
        // 28. West Bengal - 23 districts
        seedStateRegions(repo, "West Bengal", List.of(
            "Alipurduar", "Bankura", "Birbhum", "Cooch Behar", "Dakshin Dinajpur",
            "Darjeeling", "Hooghly", "Howrah", "Jalpaiguri", "Jhargram",
            "Kalimpong", "Kolkata", "Malda", "Murshidabad", "Nadia",
            "North 24 Parganas", "Paschim Bardhaman", "Paschim Medinipur", "Purba Bardhaman",
            "Purba Medinipur", "Purulia", "South 24 Parganas", "Uttar Dinajpur"
        ));
    }

    private void seedStateRegions(RegionRepository repo, String state, List<String> districts) {
        List<String> existingDistricts = repo.findByStateNameAndActiveTrueOrderByDistrictName(state)
                .stream()
                .map(Region::getDistrictName)
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        for (String d : districts) {
            if (!existingDistricts.contains(d.toLowerCase())) {
                repo.save(new Region(state, d));
            }
        }
    }

    private void seedSchemes(SchemeRepository schemeRepo, EligibilityCriteriaRepository criteriaRepo, RequiredDocumentRepository reqDocRepo, com.example.digital_subsidy.repository.DisbursementMilestoneRepository milestoneRepo) {
        if (schemeRepo.count() == 0) {
            Scheme newS1 = new Scheme();
            newS1.setName("PM Krishi Vikas Micro-Irrigation Subsidy");
            newS1.setDescription("Financial assistance and subsidy grant for small & marginal farmers to install solar pumps and drip irrigation equipment.");
            newS1.setMinAmount(new BigDecimal("20000"));
            newS1.setMaxAmount(new BigDecimal("100000"));
            newS1.setTotalBudget(new BigDecimal("50000000"));
            newS1.setRemainingBudget(new BigDecimal("48000000"));
            newS1.setTargetCategories("General,OBC,SC,ST,Minority");
            newS1.setEligibleStates("ALL");
            newS1.setCategory("Agriculture");
            newS1.setMinQualifyingScore(60);
            newS1.setRejectionCooldownDays(30);
            newS1.setStatus("ACTIVE");
            schemeRepo.save(newS1);

            Scheme newS2 = new Scheme();
            newS2.setName("National Rooftop Solar Subsidy Scheme");
            newS2.setDescription("Capital subsidy support for residential consumers installing 1kW to 5kW solar photovoltaic systems on rooftops.");
            newS2.setMinAmount(new BigDecimal("15000"));
            newS2.setMaxAmount(new BigDecimal("75000"));
            newS2.setTotalBudget(new BigDecimal("30000000"));
            newS2.setRemainingBudget(new BigDecimal("29000000"));
            newS2.setTargetCategories("General,OBC,SC,ST");
            newS2.setEligibleStates("ALL");
            newS2.setCategory("Solar");
            newS2.setMinQualifyingScore(70);
            newS2.setRejectionCooldownDays(30);
            newS2.setStatus("ACTIVE");
            schemeRepo.save(newS2);

            Scheme newS3 = new Scheme();
            newS3.setName("Digital MSME Technology & Equipment Capital Grant");
            newS3.setDescription("Grant assistance for micro & small enterprises upgrading manufacturing plant, cloud digital tools, and green technologies.");
            newS3.setMinAmount(new BigDecimal("50000"));
            newS3.setMaxAmount(new BigDecimal("250000"));
            newS3.setTotalBudget(new BigDecimal("100000000"));
            newS3.setRemainingBudget(new BigDecimal("95000000"));
            newS3.setTargetCategories("General,OBC,SC,ST,Minority");
            newS3.setEligibleStates("ALL");
            newS3.setCategory("MSME");
            newS3.setMinQualifyingScore(65);
            newS3.setRejectionCooldownDays(30);
            newS3.setStatus("ACTIVE");
            schemeRepo.save(newS3);

            Scheme newS4 = new Scheme();
            newS4.setName("Higher Education Financial Assistance Scheme");
            newS4.setDescription("Financial assistance for eligible students pursuing higher education.");
            newS4.setMinAmount(new BigDecimal("10000"));
            newS4.setMaxAmount(new BigDecimal("100000"));
            newS4.setTotalBudget(new BigDecimal("200000000"));
            newS4.setRemainingBudget(new BigDecimal("195000000"));
            newS4.setTargetCategories("General,OBC,SC,ST,Minority");
            newS4.setEligibleStates("ALL");
            newS4.setCategory("Education");
            newS4.setMinQualifyingScore(60);
            newS4.setRejectionCooldownDays(30);
            newS4.setStatus("ACTIVE");
            schemeRepo.save(newS4);
        }

        List<Scheme> allSchemes = schemeRepo.findAll();
        boolean hasEducation = allSchemes.stream().anyMatch(s -> "Education".equalsIgnoreCase(s.getCategory()) || (s.getName() != null && s.getName().toLowerCase().contains("education")));
        if (!hasEducation) {
            Scheme eduScheme = new Scheme();
            eduScheme.setName("Higher Education Financial Assistance Scheme");
            eduScheme.setDescription("Financial assistance for eligible students pursuing higher education.");
            eduScheme.setMinAmount(new BigDecimal("10000"));
            eduScheme.setMaxAmount(new BigDecimal("100000"));
            eduScheme.setTotalBudget(new BigDecimal("200000000"));
            eduScheme.setRemainingBudget(new BigDecimal("195000000"));
            eduScheme.setTargetCategories("General,OBC,SC,ST,Minority");
            eduScheme.setEligibleStates("ALL");
            eduScheme.setCategory("Education");
            eduScheme.setMinQualifyingScore(60);
            eduScheme.setRejectionCooldownDays(30);
            eduScheme.setStatus("ACTIVE");
            schemeRepo.save(eduScheme);
            allSchemes.add(eduScheme);
        }

        for (Scheme s : allSchemes) {
            if (s.getRejectionCooldownDays() == null || s.getRejectionCooldownDays() <= 0) {
                s.setRejectionCooldownDays(30);
                schemeRepo.save(s);
            }
            String nameLower = s.getName().toLowerCase();
            if (nameLower.contains("krishi") || nameLower.contains("micro-irrigation") || nameLower.contains("farmer")) {
                if (s.getCategory() == null || s.getCategory().equals("General")) s.setCategory("Agriculture");
                if (s.getMinQualifyingScore() == null) s.setMinQualifyingScore(60);
                schemeRepo.save(s);
                seedAgricultureScheme(criteriaRepo, reqDocRepo, milestoneRepo, s);
            } else if (nameLower.contains("solar") || nameLower.contains("rooftop")) {
                if (s.getCategory() == null || s.getCategory().equals("General")) s.setCategory("Solar");
                if (s.getMinQualifyingScore() == null) s.setMinQualifyingScore(70);
                schemeRepo.save(s);
                seedSolarScheme(criteriaRepo, reqDocRepo, milestoneRepo, s);
            } else if (nameLower.contains("msme") || nameLower.contains("technology")) {
                if (s.getCategory() == null || s.getCategory().equals("General")) s.setCategory("MSME");
                if (s.getMinQualifyingScore() == null) s.setMinQualifyingScore(65);
                schemeRepo.save(s);
                seedMsmeScheme(criteriaRepo, reqDocRepo, milestoneRepo, s);
            } else if (nameLower.contains("education") || nameLower.contains("student") || nameLower.contains("scholarship")) {
                if (s.getCategory() == null || s.getCategory().equals("General")) s.setCategory("Education");
                if (s.getMinQualifyingScore() == null) s.setMinQualifyingScore(60);
                schemeRepo.save(s);
                seedEducationScheme(criteriaRepo, reqDocRepo, milestoneRepo, s);
            }
        }
    }

    private void seedAgricultureScheme(EligibilityCriteriaRepository criteriaRepo, RequiredDocumentRepository reqDocRepo, com.example.digital_subsidy.repository.DisbursementMilestoneRepository milestoneRepo, Scheme s) {
        criteriaRepo.deleteByScheme(s);

        createCriteria(criteriaRepo, s, "FARMER_STATUS", "BOOLEAN", "EQUALS", "YES", 20, "Confirm active registered farmer status", "Are you a registered farmer?", "YES_NO", "YES,NO", 1);
        createCriteria(criteriaRepo, s, "LAND_OWNERSHIP", "BOOLEAN", "EQUALS", "YES", 20, "Confirm ownership of agricultural land", "Do you own agricultural land?", "YES_NO", "YES,NO", 2);
        createCriteria(criteriaRepo, s, "MAX_INCOME", "NUMERIC", "LTE", "300000", 20, "Annual family income ceiling ₹3,00,000", "What is your annual family income? (₹)", "NUMBER", null, 3);
        createCriteria(criteriaRepo, s, "LAND_HOLDING", "NUMERIC", "GTE", "0.5", 20, "Minimum agricultural land area 0.5 Acres required", "What is your agricultural land area? (Acres)", "DECIMAL", null, 4);
        createCriteria(criteriaRepo, s, "IRRIGATION_REQUIREMENT", "BOOLEAN", "EQUALS", "YES", 20, "Requirement for micro-irrigation equipment", "Do you have irrigation needs?", "YES_NO", "YES,NO", 5);

        createOrUpdateRequiredDoc(reqDocRepo, s, "Aadhaar Card", "AADHAAR", true, "PDF, JPG, JPEG, PNG", 5, 1);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Bank Passbook", "BANK_PASSBOOK", true, "PDF, JPG, JPEG, PNG", 5, 2);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Land Ownership Document", "LAND_OWNERSHIP", true, "PDF, JPG, JPEG, PNG", 5, 3);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Income Certificate", "INCOME_CERTIFICATE", true, "PDF, JPG, JPEG, PNG", 5, 4);

        seedSchemeMilestoneIfEmpty(milestoneRepo, s, List.of(
            new MilestoneSeed("Initial Equipment Sanction", 30, 1, "First tranche upon equipment sanction approval", "EQUIPMENT_PURCHASE_INVOICE"),
            new MilestoneSeed("Installation Verification", 40, 2, "Second tranche upon drip/pump installation proof", "UTILIZATION_CERTIFICATE"),
            new MilestoneSeed("Final Field Operational Sign-off", 30, 3, "Final tranche upon field officer verification", "FINAL_UTILIZATION_CERTIFICATE")
        ));
    }

    private void seedSolarScheme(EligibilityCriteriaRepository criteriaRepo, RequiredDocumentRepository reqDocRepo, com.example.digital_subsidy.repository.DisbursementMilestoneRepository milestoneRepo, Scheme s) {
        criteriaRepo.deleteByScheme(s);

        createCriteria(criteriaRepo, s, "PROPERTY_OWNERSHIP", "BOOLEAN", "EQUALS", "YES", 20, "Confirm ownership of residential property", "Do you own the property?", "YES_NO", "YES,NO", 1);
        createCriteria(criteriaRepo, s, "ELECTRICITY_CONNECTION", "BOOLEAN", "EQUALS", "YES", 20, "Active electricity meter connection", "Do you have a valid electricity connection?", "YES_NO", "YES,NO", 2);
        createCriteria(criteriaRepo, s, "PROPERTY_TYPE", "CATEGORICAL", "EQUALS", "RESIDENTIAL", 20, "Property classification type", "What type of property is it?", "SINGLE_SELECT", "RESIDENTIAL,COMMERCIAL,OTHER", 3);
        createCriteria(criteriaRepo, s, "SOLAR_CAPACITY", "NUMERIC", "GTE", "1", 20, "Proposed solar plant capacity (1 to 5 kW)", "What solar capacity are you applying for? (kW)", "NUMBER", null, 4);
        createCriteria(criteriaRepo, s, "MAX_INCOME", "NUMERIC", "LTE", "600000", 20, "Annual household income limit ₹6,00,000", "What is your annual household income? (₹)", "NUMBER", null, 5);

        reqDocRepo.deleteByScheme(s);

        createOrUpdateRequiredDoc(reqDocRepo, s, "Aadhaar Card", "AADHAAR", true, "PDF, JPG, JPEG, PNG", 5, 1);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Bank Passbook", "BANK_PASSBOOK", true, "PDF, JPG, JPEG, PNG", 5, 2);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Latest Electricity Bill", "ELECTRICITY_BILL", true, "PDF, JPG, JPEG, PNG", 5, 3);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Income Certificate", "INCOME_CERTIFICATE", true, "PDF, JPG, JPEG, PNG", 5, 4);

        seedSchemeMilestoneIfEmpty(milestoneRepo, s, List.of(
            new MilestoneSeed("Rooftop Structure Inspection", 30, 1, "First tranche upon structural feasibility approval", "INSTALLATION_WORK_ORDER"),
            new MilestoneSeed("Solar Panel & Inverter Installation", 40, 2, "Second tranche upon panel mounting and wiring", "INSTALLATION_PROGRESS_REPORT"),
            new MilestoneSeed("Grid Synchronization & Commissioning", 30, 3, "Final tranche upon net metering activation", "COMMISSIONING_CERTIFICATE")
        ));
    }

    private void seedMsmeScheme(EligibilityCriteriaRepository criteriaRepo, RequiredDocumentRepository reqDocRepo, com.example.digital_subsidy.repository.DisbursementMilestoneRepository milestoneRepo, Scheme s) {
        criteriaRepo.deleteByScheme(s);

        createCriteria(criteriaRepo, s, "MIN_AGE", "NUMERIC", "GTE", "21", 15, "Entrepreneur age 21 or older", "Applicant age 21 or older?", "NUMBER", null, 1);
        createCriteria(criteriaRepo, s, "MAX_INCOME", "NUMERIC", "LTE", "1000000", 35, "Annual business turnover ceiling ₹10,00,000", "What is your annual business turnover? (₹)", "NUMBER", null, 2);
        createCriteria(criteriaRepo, s, "BUSINESS_TYPE", "CATEGORICAL", "IN", "MANUFACTURING,SERVICE,TRADING,MICRO_ENTERPRISE,SMALL_ENTERPRISE", 15, "Registered enterprise category", "What is your business type?", "SINGLE_SELECT", "MANUFACTURING,SERVICE,TRADING,OTHER", 3);
        createCriteria(criteriaRepo, s, "REQUIRED_DOCS", "BOOLEAN", "IS_TRUE", "TRUE", 25, "Required supporting document uploads", "Have you uploaded required supporting documents?", "YES_NO", "YES,NO", 4);
        createCriteria(criteriaRepo, s, "EQUIPMENT_INVESTMENT", "NUMERIC", "GTE", "50000", 10, "Planned technology & equipment investment", "What is your planned technology/equipment investment? (₹)", "NUMBER", null, 5);

        createOrUpdateRequiredDoc(reqDocRepo, s, "Aadhaar Card", "AADHAAR", true, "PDF, JPG, JPEG, PNG", 5, 1);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Udyam / Business Registration", "UDYAM_CERTIFICATE", true, "PDF, JPG, JPEG, PNG", 5, 2);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Enterprise Proof", "ENTERPRISE_PROOF", true, "PDF, JPG, JPEG, PNG", 5, 3);

        seedSchemeMilestoneIfEmpty(milestoneRepo, s, List.of(
            new MilestoneSeed("Equipment Procurement Order", 40, 1, "First tranche upon equipment PO submission", "EQUIPMENT_PURCHASE_INVOICE"),
            new MilestoneSeed("Technology Integration Audit", 30, 2, "Second tranche upon software/tool integration", "UTILIZATION_CERTIFICATE"),
            new MilestoneSeed("Production & Commercial Go-Live", 30, 3, "Final tranche upon successful commercial operation", "FINAL_UTILIZATION_CERTIFICATE")
        ));
    }

    private void seedEducationScheme(EligibilityCriteriaRepository criteriaRepo, RequiredDocumentRepository reqDocRepo, com.example.digital_subsidy.repository.DisbursementMilestoneRepository milestoneRepo, Scheme s) {
        criteriaRepo.deleteByScheme(s);

        createCriteria(criteriaRepo, s, "MAX_INCOME", "NUMERIC", "LTE", "300000", 20, "Annual family income limit ₹3,00,000", "What is your annual family income? (₹)", "NUMBER", null, 1);
        createCriteria(criteriaRepo, s, "ACADEMIC_PERFORMANCE", "NUMERIC", "GTE", "50", 20, "Previous academic percentage or CGPA (Minimum 50%)", "What was your previous academic percentage/CGPA?", "DECIMAL", null, 2);
        createCriteria(criteriaRepo, s, "ENROLLMENT_STATUS", "BOOLEAN", "EQUALS", "YES", 15, "Are you currently enrolled in an educational institution?", "Are you currently enrolled in an educational institution?", "YES_NO", "YES,NO", 3);
        createCriteria(criteriaRepo, s, "SCHOLARSHIP_STATUS", "BOOLEAN", "EQUALS", "NO", 15, "Are you already receiving another scholarship or education subsidy?", "Are you already receiving another scholarship or education subsidy?", "YES_NO", "YES,NO", 4);
        createCriteria(criteriaRepo, s, "EDUCATION_LEVEL", "CATEGORICAL", "IN", "Intermediate / 12th,Diploma,Undergraduate,Postgraduate,PhD,Other", 10, "What is your current education level?", "What is your current education level?", "SINGLE_SELECT", "Intermediate / 12th,Diploma,Undergraduate,Postgraduate,PhD,Other", 5);
        createCriteria(criteriaRepo, s, "INSTITUTION_TYPE", "CATEGORICAL", "IN", "Government,Government Aided,Private,Other", 10, "What type of institution are you studying in?", "What type of institution are you studying in?", "SINGLE_SELECT", "Government,Government Aided,Private,Other", 6);
        createCriteria(criteriaRepo, s, "STUDY_YEAR", "CATEGORICAL", "IN", "1st Year,2nd Year,3rd Year,4th Year,Final Year,Other", 5, "What is your current year of study?", "What is your current year of study?", "SINGLE_SELECT", "1st Year,2nd Year,3rd Year,4th Year,Final Year,Other", 7);
        createCriteria(criteriaRepo, s, "COURSE_DURATION", "NUMERIC", "GTE", "1", 5, "Course duration in years (Minimum 1 year)", "What is your course duration in years?", "NUMBER", null, 8);

        createOrUpdateRequiredDoc(reqDocRepo, s, "Income Certificate", "INCOME_CERTIFICATE", true, "PDF, JPG, JPEG, PNG", 5, 1);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Bonafide / Student Certificate", "BONAFIDE_CERTIFICATE", true, "PDF, JPG, JPEG, PNG", 5, 2);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Previous Academic Marksheet", "ACADEMIC_MARKSHEET", true, "PDF, JPG, JPEG, PNG", 5, 3);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Admission Proof / Fee Structure", "ADMISSION_PROOF", true, "PDF, JPG, JPEG, PNG", 5, 4);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Aadhaar Card", "AADHAAR", true, "PDF, JPG, JPEG, PNG", 5, 5);
        createOrUpdateRequiredDoc(reqDocRepo, s, "Bank Passbook", "BANK_PASSBOOK", true, "PDF, JPG, JPEG, PNG", 5, 6);

        seedSchemeMilestoneIfEmpty(milestoneRepo, s, List.of(
            new MilestoneSeed("Admission Verification", 30, 1, "First tranche upon admission verification", "ADMISSION_PROOF"),
            new MilestoneSeed("Fee Verification", 40, 2, "Second tranche upon fee receipt submission", "FEE_PAYMENT_RECEIPT"),
            new MilestoneSeed("Academic Continuation", 30, 3, "Final tranche upon mid-term continuation proof", "COURSE_PROGRESS_CERTIFICATE")
        ));
    }

    private static record MilestoneSeed(String name, int pct, int order, String desc, String requiredDocumentType) {}

    private void seedSchemeMilestoneIfEmpty(com.example.digital_subsidy.repository.DisbursementMilestoneRepository repo, Scheme scheme, List<MilestoneSeed> seeds) {
        List<com.example.digital_subsidy.model.DisbursementMilestone> existing = repo.findBySchemeIdOrderByDisplayOrderAsc(scheme.getId());
        if (existing.isEmpty()) {
            for (MilestoneSeed seed : seeds) {
                repo.save(new com.example.digital_subsidy.model.DisbursementMilestone(scheme, seed.name(), seed.pct(), seed.order(), true, seed.desc(), seed.requiredDocumentType()));
            }
        } else {
            for (int i = 0; i < existing.size() && i < seeds.size(); i++) {
                com.example.digital_subsidy.model.DisbursementMilestone dm = existing.get(i);
                MilestoneSeed seed = seeds.get(i);
                if (dm.getRequiredDocumentType() == null || dm.getRequiredDocumentType().isBlank()) {
                    dm.setRequiredDocumentType(seed.requiredDocumentType());
                    repo.save(dm);
                }
            }
        }
    }

    private void createCriteria(EligibilityCriteriaRepository repo, Scheme scheme, String name, String type, String op, String val, int weight, String desc, String qText, String ansType, String opts, int order) {
        EligibilityCriteria c = new EligibilityCriteria();
        c.setScheme(scheme);
        c.setCriterionName(name);
        c.setCriterionType(type);
        c.setOperator(op);
        c.setTargetValue(val);
        c.setWeightPoints(weight);
        c.setDescription(desc);
        c.setQuestionText(qText);
        c.setAnswerType(ansType);
        c.setOptions(opts);
        c.setDisplayOrder(order);
        c.setRequired(true);
        c.setActive(true);
        repo.save(c);
    }

    private void createOrUpdateRequiredDoc(RequiredDocumentRepository repo, Scheme scheme, String name, String type, boolean req, String allowed, int maxSize, int order) {
        List<RequiredDocument> existingDocs = repo.findByScheme(scheme);
        java.util.Optional<RequiredDocument> docOpt = existingDocs.stream()
                .filter(d -> d.getDocumentName().equalsIgnoreCase(name) || (d.getDocumentType() != null && d.getDocumentType().equalsIgnoreCase(type)))
                .findFirst();

        if (docOpt.isPresent()) {
            RequiredDocument rd = docOpt.get();
            rd.setDocumentName(name);
            rd.setDocumentType(type);
            rd.setRequired(req);
            rd.setAllowedFileTypes(allowed);
            rd.setMaxFileSizeMb(maxSize);
            rd.setDisplayOrder(order);
            rd.setActive(true);
            repo.save(rd);
        } else {
            RequiredDocument rd = new RequiredDocument(scheme, name, type, req, allowed, maxSize, order, true);
            repo.save(rd);
        }
    }
}
