package ru.quietclinic.communications;

interface NotificationPort {
  DeliveryResult sendNeutral(String destinationRef, String templateKey);
}
