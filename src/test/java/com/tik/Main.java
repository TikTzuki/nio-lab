package com.tik;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class Main {
    @BeforeClass
    void setup() {

    }

    @Test
    void test() {
        System.out.println("Hello World");
    }

    @AfterClass
    void teardown() {

    }
}
