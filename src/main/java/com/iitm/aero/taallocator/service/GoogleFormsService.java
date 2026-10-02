package com.iitm.aero.taallocator.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.forms.v1.Forms;
import com.google.api.services.forms.v1.FormsScopes;
import com.google.api.services.forms.v1.model.BatchUpdateFormRequest;
import com.google.api.services.forms.v1.model.ChoiceQuestion;
import com.google.api.services.forms.v1.model.Form;
import com.google.api.services.forms.v1.model.Item;
import com.google.api.services.forms.v1.model.Option;
import com.google.api.services.forms.v1.model.Question;
import com.google.api.services.forms.v1.model.QuestionItem;
import com.google.api.services.forms.v1.model.Request;
import com.google.api.services.forms.v1.model.UpdateItemRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoogleFormsService {

    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private StudentRepository studentRepository;

    private static final String APPLICATION_NAME = "TA Allocator";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private Forms getFormsService() throws Exception {
        InputStream in = new ClassPathResource("credentials.json").getInputStream();
        GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                .createScoped(Collections.singleton(FormsScopes.FORMS_BODY));
        
        return new Forms.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), 
                JSON_FACTORY, 
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void updateForm(double groupId, String formId) {
        System.out.println("Starting Google Forms API Update...");
        try {
            Forms formsService = getFormsService();
            Form form = formsService.forms().get(formId).execute();

            List<Course> courses = courseRepository.findByGroupId(groupId);
            List<Student> unassignedStudents = studentRepository.findByIsAvailableTrue();
            
            List<String> courseNames = courses.stream()
                    .map(Course::getCourseNo)
                    .collect(Collectors.toList());
                    
            List<String> studentOptions = unassignedStudents.stream()
                    .map(s -> s.getRollNo() + ": " + s.getName() + " [" + s.getCgpa() + "] (" + s.getProgram() + ")")
                    .collect(Collectors.toList());
                    
            studentOptions.add(0, "No preference. You choose for me.");
            studentOptions.add(1, "I do not want TAs.");

            List<Request> requests = new ArrayList<>();

            for (Item item : form.getItems()) {
                if (item.getTitle() == null) continue;
                
                List<String> optionsToSet = null;
                
                if (item.getTitle().contains("Select Course")) {
                    optionsToSet = courseNames;
                } else if (item.getTitle().contains("TA Preference")) {
                    optionsToSet = studentOptions;
                }

                if (optionsToSet != null && item.getQuestionItem() != null) {
                    List<Option> optionsList = optionsToSet.stream()
                            .map(opt -> new Option().setValue(opt))
                            .collect(Collectors.toList());

                    Question question = item.getQuestionItem().getQuestion();
                    if (question.getChoiceQuestion() == null) {
                        question.setChoiceQuestion(new ChoiceQuestion().setType("DROP_DOWN"));
                    }
                    question.getChoiceQuestion().setOptions(optionsList);

                    Item updatedItem = new Item()
                            .setItemId(item.getItemId())
                            .setTitle(item.getTitle())
                            .setQuestionItem(new QuestionItem().setQuestion(question));

                    requests.add(new Request().setUpdateItem(new UpdateItemRequest()
                            .setItem(updatedItem)
                            .setLocation(new com.google.api.services.forms.v1.model.Location().setIndex(item.getItemId() != null ? 0 : 0)) // Index doesn't matter for UpdateItem by ID usually, but required field sometimes. Actually Location.index is used for CreateItem. For UpdateItem it uses updateMask.
                            .setUpdateMask("questionItem.question.choiceQuestion.options")
                    ));
                }
            }

            if (!requests.isEmpty()) {
                BatchUpdateFormRequest batchRequest = new BatchUpdateFormRequest().setRequests(requests);
                formsService.forms().batchUpdate(formId, batchRequest).execute();
                System.out.println("Successfully updated Google Form ID: " + formId);
            } else {
                System.out.println("No matching questions found in the form to update. Ensure you have questions titled 'Select Course' and 'TA Preference X'.");
            }

        } catch (Exception e) {
            System.err.println("Failed to update Google Form. Did you put credentials.json in src/main/resources/? Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
