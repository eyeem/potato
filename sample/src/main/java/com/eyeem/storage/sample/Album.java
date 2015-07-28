package com.eyeem.storage.sample;

import com.eyeem.storage.annotation.Id;
import com.eyeem.storage.annotation.Storage;

import java.io.Serializable;

/**
 * Created by budius on 28.07.15.
 */
@Storage
public class Album implements Serializable {
    @Id
    public String id;
    public String name;
    public String description;
}
