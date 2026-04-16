package com.ai.learningdetection.security;

import com.ai.learningdetection.entity.Admin;
import com.ai.learningdetection.entity.Parent;
import com.ai.learningdetection.entity.Teacher;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

/**
 * Loads user by email — checks Admin, Teacher, then Parent collection in Firestore.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final Firestore firestore;

    private static final String ADMINS_COLLECTION = "admins";
    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PARENTS_COLLECTION = "parents";

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            // 1. Check Admin Collection (smallest, check first)
            QuerySnapshot adminQuery = firestore.collection(ADMINS_COLLECTION)
                    .whereEqualTo("email", email)
                    .limit(1).get().get();

            if (!adminQuery.isEmpty()) {
                Admin admin = adminQuery.getDocuments().get(0).toObject(Admin.class);
                return new AdminUserDetails(admin);
            }

            // 2. Check Teacher Collection
            QuerySnapshot teacherQuery = firestore.collection(TEACHERS_COLLECTION)
                    .whereEqualTo("email", email)
                    .limit(1).get().get();
            
            if (!teacherQuery.isEmpty()) {
                Teacher teacher = teacherQuery.getDocuments().get(0).toObject(Teacher.class);
                return new TeacherUserDetails(teacher);
            }

            // 3. Check Parent Collection
            QuerySnapshot parentQuery = firestore.collection(PARENTS_COLLECTION)
                    .whereEqualTo("email", email)
                    .limit(1).get().get();
            
            if (!parentQuery.isEmpty()) {
                Parent parent = parentQuery.getDocuments().get(0).toObject(Parent.class);
                return new ParentUserDetails(parent);
            }

            throw new UsernameNotFoundException("No account found with email in Firestore: " + email);
        } catch (InterruptedException | ExecutionException e) {
            throw new UsernameNotFoundException("Firestore error fetching user: " + email, e);
        }
    }
}

