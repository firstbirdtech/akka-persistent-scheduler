# Akka Persistent Scheduler

[![Maven Central](https://img.shields.io/maven-central/v/com.firstbird/akka-persistent-scheduler_2.13.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.firstbird%22%20AND%20a:%22akka-persistent-scheduler_2.13%22)
[![Github Actions CI Workflow](https://github.com/firstbirdtech/akka-persistent-scheduler/workflows/CI/badge.svg)](https://github.com/firstbirdtech/akka-persistent-scheduler/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/firstbirdtech/akka-persistent-scheduler/branch/master/graph/badge.svg?token=5YeJOEl3UV)](https://codecov.io/gh/firstbirdtech/akka-persistent-scheduler)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

This is an extension for the Akka Scheduler to allow durable scheduling for specific a specific time in the future.