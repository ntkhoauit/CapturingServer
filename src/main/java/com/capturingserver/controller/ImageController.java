package com.capturingserver.controller;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.capturingserver.services.ImageService;
import com.capturingserver.utils.CommonRESTRegistry;
import com.google.gson.Gson;

@CrossOrigin(origins = "*", maxAge = 360000)
@RestController
@RequestMapping
public class ImageController {
    @Autowired
    ImageService imageService;

    @PostMapping(value = CommonRESTRegistry.CAPTURING_IMAGE)
    public ResponseEntity upload3dZipFolder(@RequestParam("data") String modelPathsString) {
        List<String> modelPathList = new Gson().fromJson(modelPathsString, List.class);
        imageService.handle3dZipFolder(modelPathList);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
