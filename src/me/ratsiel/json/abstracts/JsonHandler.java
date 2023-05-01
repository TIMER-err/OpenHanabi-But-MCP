package me.ratsiel.json.abstracts;

public abstract class JsonHandler<T> {
   public abstract Object serialize(JsonValue var1);

   public abstract JsonValue deserialize(T var1);
}
