package gog.my_project.data_base.query.example.v3.queries

import gog.my_project.data_base.query.example.v1.queries.IExampleV1

/** Keeps the existing example runner contract while distinguishing v3 examples. */
interface IExampleV3<T> : IExampleV1<T>
